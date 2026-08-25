package org.asdf.lapisPlugin.tcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.asdf.lapisPlugin.LapisPlugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class TcpManager {

    private final LapisPlugin plugin;
    private final int port;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread serverThread;
    private volatile boolean running = false;
    private final Object writeLock = new Object();

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
        closeQuietly(clientSocket);
        closeQuietly(serverSocket);
    }

    private void closeQuietly(Closeable c) {
        try { if (c != null) c.close(); } catch (IOException ignored) {}
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(port);
            plugin.getLogger().info("Lapis TCP server listening on port " + port);

            while (running) {
                clientSocket = serverSocket.accept();
                plugin.getLogger().info("Python client connected: " + clientSocket.getInetAddress());

                in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));

                while (running && isConnected()) {
                    try {
                        // 1. 读 4 字节大端长度
                        int length = in.readInt();
                        if (length <= 0 || length > 1024 * 1024) {
                            plugin.getLogger().warning("Invalid packet length: " + length);
                            break;
                        }

                        // 2. 读 payload
                        byte[] bytes = new byte[length];
                        in.readFully(bytes);
                        String raw = new String(bytes, StandardCharsets.UTF_8);
                        JsonObject msg = JsonParser.parseString(raw).getAsJsonObject();

                        // 3. 判断是 command（有 request_id）还是其他
                        if (msg.has("request_id")) {
                            // command 必须在主线程处理（可能调 Bukkit API）
                            // 处理完后要回 response
                            final JsonObject message = msg;
                            CompletableFuture<JsonObject> future = new CompletableFuture<>();

                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    JsonObject response = CommandHandler.handleAndReturn(message);
                                    future.complete(response);
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            });

                            try {
                                JsonObject response = future.get(); // 阻塞等主线程
                                if (response != null) send(response);
                            } catch (Exception e) {
                                JsonObject err = new JsonObject();
                                err.addProperty("request_id", message.get("request_id").getAsString());
                                err.addProperty("message_type", "error");
                                err.addProperty("error", e.getMessage());
                                send(err);
                            }
                        } else {
                            // 无 request_id，可能是心跳或其他，先忽略
                            plugin.getLogger().info("Received message without request_id: " + msg);
                        }

                    } catch (EOFException e) {
                        plugin.getLogger().info("Python client disconnected");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            if (running) plugin.getLogger().severe("TCP error: " + e.getMessage());
        }
    }

    // 发送：4字节大端长度 + JSON bytes
    public void send(JsonObject message) {
        if (out == null) return;
        try {
            byte[] bytes = message.toString().getBytes(StandardCharsets.UTF_8);
            synchronized (writeLock) {
                out.writeInt(bytes.length);
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Send failed: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }
}