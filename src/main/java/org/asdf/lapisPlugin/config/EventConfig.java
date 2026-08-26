package org.asdf.lapisPlugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EventConfig {
    private final JavaPlugin plugin;
    private Set<String> enabledEvents = new HashSet<>();
    private boolean allowAll = false;

    public EventConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "event.yml");
        if (!file.exists()) {
            plugin.saveResource("event.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> list = config.getStringList("enabled-events");

        if (list == null || list.isEmpty()) {
            allowAll = true;
            enabledEvents.clear();
            plugin.getLogger().info("[EventConfig] Whitelist is empty, allowing all defined events.");
        } else {
            allowAll = false;
            enabledEvents = new HashSet<>();
            for (String s : list) {
                if (s != null && !s.isBlank()) {
                    enabledEvents.add(s.trim());
                }
            }
            plugin.getLogger().info("[EventConfig] Loaded whitelist: " + enabledEvents);
        }
    }

    public boolean isEnabled(String eventType) {
        if (allowAll) return true;
        return enabledEvents.contains(eventType);
    }

    public Set<String> getEnabledEvents() {
        return Collections.unmodifiableSet(enabledEvents);
    }

    public boolean isAllowAll() {
        return allowAll;
    }
}