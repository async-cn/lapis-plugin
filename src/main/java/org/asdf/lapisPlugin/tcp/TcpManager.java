package org.asdf.lapisPlugin.tcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.asdf.lapisPlugin.LapisPlugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class TcpManager {

    private final LapisPlugin plugin;
    private final int port;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    // package_name -> 连接会话，用于发 event
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public TcpManager(LapisPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    public void start() {
        running = true;
        serverThread = new Thread(this::runServer, "Lapis-TCP");
        serverThread.start();
    }

    public void shutdown() {
        running = false;
        closeQuietly(serverSocket);
        sessions.values().forEach(s -> closeQuietly(s.socket));
    }

    private void closeQuietly(Closeable c) {
        try { if (c != null) c.close(); } catch (IOException ignored) {}
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(port);
            plugin.getLogger().info("Lapis TCP server listening on port " + port);

            while (running) {
                Socket socket = serverSocket.accept();
                plugin.getLogger().info("Client connected: " + socket.getInetAddress());
                new Thread(() -> handleClient(socket), "Lapis-Client-" + socket.getInetAddress()).start();
            }
        } catch (IOException e) {
            if (running) plugin.getLogger().severe("TCP server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        DataOutputStream out = null;
        String packageName = null;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            ClientSession session = new ClientSession(socket, out);
            boolean handshaked = false;

            while (running && !socket.isClosed()) {
                int length = in.readInt();
                if (length <= 0 || length > 1024 * 1024) {
                    plugin.getLogger().warning("Invalid packet length: " + length);
                    break;
                }

                byte[] bytes = new byte[length];
                in.readFully(bytes);
                String raw = new String(bytes, StandardCharsets.UTF_8);
                JsonObject msg = JsonParser.parseString(raw).getAsJsonObject();

                if (!msg.has("id")) {
                    plugin.getLogger().warning("Received message without id: " + raw);
                    continue;
                }

                int id = msg.get("id").getAsInt();

                // 必须先 handshake
                if (!handshaked) {
                    String cmdType = msg.has("command_type") ? msg.get("command_type").getAsString() : "";
                    if (!"handshake".equals(cmdType)) {
                        JsonObject err = new JsonObject();
                        err.addProperty("response_type", "error_response");
                        err.addProperty("id", id);
                        err.addProperty("ok", false);
                        JsonObject d = new JsonObject();
                        d.addProperty("error", "Handshake required");
                        err.add("data", d);
                        send(out, err);
                        break;
                    }

                    JsonObject resp = CommandHandler.handleHandshake(msg);
                    send(out, resp);

                    if (resp.has("ok") && resp.get("ok").getAsBoolean()) {
                        handshaked = true;
                        packageName = msg.getAsJsonObject("data").get("package_name").getAsString();
                        session.packageName = packageName;
                        sessions.put(packageName, session);
                        plugin.getLogger().info("Handshake success: " + packageName);
                    } else {
                        break; // 握手失败，断开
                    }
                    continue;
                }

                // 已握手，正常处理 command
                final JsonObject command = msg;
                final String finalPackageName = packageName;
                final DataOutputStream finalOut = out;

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    JsonObject response = CommandHandler.handle(command, finalPackageName);
                    send(finalOut, response);
                });
            }

        } catch (EOFException e) {
            plugin.getLogger().info("Client disconnected: " + socket.getInetAddress());
        } catch (IOException e) {
            plugin.getLogger().warning("Client error: " + e.getMessage());
        } finally {
            if (packageName != null) sessions.remove(packageName);
            closeQuietly(socket);
        }
    }

    private void send(DataOutputStream out, JsonObject msg) {
        try {
            byte[] bytes = msg.toString().getBytes(StandardCharsets.UTF_8);
            synchronized (out) {
                out.writeInt(bytes.length);
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Send failed: " + e.getMessage());
        }
    }

    // 发 event 给指定 package
    public void sendEvent(String packageName, JsonObject message) {
        ClientSession session = sessions.get(packageName);
        if (session != null) {
            send(session.out, message);
        }
    }

    private static class ClientSession {
        final Socket socket;
        final DataOutputStream out;
        String packageName;

        ClientSession(Socket socket, DataOutputStream out) {
            this.socket = socket;
            this.out = out;
        }
    }
}