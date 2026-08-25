package org.asdf.lapisPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.asdf.lapisPlugin.event.EventBridge;
import org.asdf.lapisPlugin.tcp.TcpManager;

public class LapisPlugin extends JavaPlugin {

    private static LapisPlugin instance;
    private EventBridge eventBridge;
    private TcpManager tcpManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        int port = getConfig().getInt("tcp-port", 9331);

        eventBridge = new EventBridge(this);
        tcpManager = new TcpManager(this, port);
        tcpManager.start();

        getLogger().info("Lapis enabled on TCP port " + port);
    }

    @Override
    public void onDisable() {
        if (tcpManager != null) {
            tcpManager.shutdown();
        }
        getLogger().info("Lapis disabled");
    }

    public static LapisPlugin getInstance() {
        return instance;
    }

    public EventBridge getEventBridge() {
        return eventBridge;
    }

    public TcpManager getTcpManager() {
        return tcpManager;
    }
}