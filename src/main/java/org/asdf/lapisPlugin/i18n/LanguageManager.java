package org.asdf.lapisPlugin.i18n;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LanguageManager {
    private final JavaPlugin plugin;
    private YamlConfiguration lang;
    private String currentLang;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.currentLang = language;
        String fileName = "language/" + language + ".yml";

        InputStream stream = plugin.getResource(fileName);
        if (stream == null) {
            plugin.getLogger().warning("Language file not found: " + fileName + ", falling back to en");
            stream = plugin.getResource("language/en.yml");
            if (stream == null) {
                plugin.getLogger().severe("Default language file not found!");
                this.lang = new YamlConfiguration();
                return;
            }
        }

        this.lang = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
        plugin.getLogger().info("Language loaded: " + language);
    }

    /**
     * 获取翻译文本，自动将 & 转换为颜色代码
     */
    public String get(String key) {
        if (lang == null) return key;
        String value = lang.getString(key);
        if (value == null) return key;
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public String get(String key, Object... args) {
        return String.format(get(key), args);
    }

    public String getCurrentLang() {
        return currentLang;
    }
}