package org.asdf.lapisPlugin;

import net.milkbowl.vault.economy.Economy;
import org.asdf.lapisPlugin.pdc.PdcManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.asdf.lapisPlugin.command.LapisCommand;
import org.asdf.lapisPlugin.config.EventConfig;
import org.asdf.lapisPlugin.event.EventBridge;
import org.asdf.lapisPlugin.i18n.LanguageManager;
import org.asdf.lapisPlugin.tcp.TcpManager;
import org.asdf.lapisPlugin.askInput.AskInputManager;

public class LapisPlugin extends JavaPlugin {

    private static LapisPlugin instance;
    private EventBridge eventBridge;
    private TcpManager tcpManager;
    private LanguageManager languageManager;
    private EventConfig eventConfig;
    private PdcManager pdcManager;
    private Economy economy;
    private AskInputManager askInputManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.eventConfig = new EventConfig(this);
        this.eventConfig.load();

        this.languageManager = new LanguageManager(this);
        this.languageManager.load(getConfig().getString("language", "zhcn"));

        pdcManager = new PdcManager(this);

        setupEconomy();

        askInputManager = new AskInputManager(this);

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

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found, economy commands will be unavailable");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("No economy provider found, economy commands will be unavailable");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("Economy provider: " + economy.getName());
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

    public PdcManager getPdcManager() {
        return pdcManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public AskInputManager getAskInputManager() {
        return askInputManager;
    }
}