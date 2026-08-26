package org.asdf.lapisPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.asdf.lapisPlugin.command.LapisCommand;
import org.asdf.lapisPlugin.config.EventConfig;
import org.asdf.lapisPlugin.event.EventBridge;
import org.asdf.lapisPlugin.i18n.LanguageManager;
import org.asdf.lapisPlugin.tcp.TcpManager;

public class LapisPlugin extends JavaPlugin {

    private static LapisPlugin instance;
    private EventBridge eventBridge;
    private TcpManager tcpManager;
    private LanguageManager languageManager;
    private EventConfig eventConfig;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.eventConfig = new EventConfig(this);
        this.eventConfig.load();

        this.languageManager = new LanguageManager(this);
        this.languageManager.load(getConfig().getString("language", "zhcn"));

        int port = getConfig().getInt("tcp-port", 9331);

        this.eventBridge = new EventBridge(this, this.eventConfig);
        this.tcpManager = new TcpManager(this, port);
        this.tcpManager.start();

        LapisCommand cmd = new LapisCommand(this);
        getCommand("lapis").setExecutor(cmd);
        getCommand("lapis").setTabCompleter(cmd);

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

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public EventConfig getEventConfig() {
        return eventConfig;
    }
}