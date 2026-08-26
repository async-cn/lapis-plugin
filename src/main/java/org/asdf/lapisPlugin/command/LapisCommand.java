package org.asdf.lapisPlugin.command;

import org.asdf.lapisPlugin.LapisPlugin;
import org.asdf.lapisPlugin.event.EventTypeMap;
import org.asdf.lapisPlugin.i18n.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LapisCommand implements CommandExecutor, TabCompleter {

    private final LapisPlugin plugin;
    private final LanguageManager lang;

    public LapisCommand(LapisPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lapis.admin")) {
            sender.sendMessage(lang.get("command.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "status" -> sendStatus(sender);
            case "list" -> sendList(sender);
            case "events" -> sendEvents(sender);
            case "reload" -> doReload(sender);
            case "enable", "disable" -> sender.sendMessage(lang.get("command.not_implemented"));
            default -> sender.sendMessage(lang.get("command.unknown"));
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(lang.get("command.help.title"));
        sender.sendMessage(lang.get("command.help.help"));
        sender.sendMessage(lang.get("command.help.status"));
        sender.sendMessage(lang.get("command.help.list"));
        sender.sendMessage(lang.get("command.help.reload"));
        sender.sendMessage(lang.get("command.help.enable"));
        sender.sendMessage(lang.get("command.help.disable"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(lang.get("command.status.title"));

        boolean connected = plugin.getTcpManager().isConnected();
        sender.sendMessage(connected
                ? lang.get("command.status.tcp_connected")
                : lang.get("command.status.tcp_disconnected"));

        var packages = plugin.getTcpManager().getConnectedPackages();
        sender.sendMessage(lang.get("command.status.packages", packages.size()));
        for (String pkg : packages) {
            sender.sendMessage(lang.get("command.status.package_prefix", pkg));
        }

        var bridge = plugin.getEventBridge();
        sender.sendMessage(lang.get("command.status.active_events", bridge.getActiveEventTypeCount()));
        sender.sendMessage(lang.get("command.status.total_listeners", bridge.getTotalListenerCount()));
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage(lang.get("command.list.title"));

        var map = plugin.getEventBridge().getRegistrySnapshot();
        if (map.isEmpty()) {
            sender.sendMessage(lang.get("command.list.no_listeners"));
            return;
        }

        for (var entry : map.entrySet()) {
            String eventType = entry.getKey();
            var listeners = entry.getValue();
            sender.sendMessage(lang.get("command.list.event_type", eventType, listeners.size()));
            for (var info : listeners) {
                String uuidShort = info.uuid().toString().substring(0, 8);
                sender.sendMessage(lang.get("command.list.listener_info", info.packageName(), uuidShort));
            }
        }
    }

    private void doReload(CommandSender sender) {
        plugin.reloadConfig();
        lang.load(plugin.getConfig().getString("language", "zhcn"));
        plugin.getEventConfig().load();
        sender.sendMessage(lang.get("command.reload"));
    }

    private void sendEvents(CommandSender sender) {
        sender.sendMessage(lang.get("command.events.title"));

        var eventConfig = plugin.getEventConfig();
        if (eventConfig.isAllowAll()) {
            sender.sendMessage(lang.get("command.events.mode_all"));
        } else {
            sender.sendMessage(lang.get("command.events.mode_whitelist"));
            sender.sendMessage(lang.get("command.events.enabled_list"));
            for (String evt : eventConfig.getEnabledEvents()) {
                sender.sendMessage(lang.get("command.events.item", evt));
            }
        }

        sender.sendMessage(lang.get("command.events.supported"));
        for (String evt : EventTypeMap.getAllSupported()) {
            boolean enabled = eventConfig.isEnabled(evt);
            String status = enabled ? lang.get("command.events.status_on") : lang.get("command.events.status_off");
            sender.sendMessage(lang.get("command.events.entry", evt, status));
        }
    }

    // ==================== Tab Complete ====================

    private static final List<String> SUB_COMMANDS = Arrays.asList("help", "status", "list", "reload", "enable", "disable");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lapis.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(input)) {
                    matches.add(sub);
                }
            }
            return matches;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            String input = args[1].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String pkg : plugin.getTcpManager().getConnectedPackages()) {
                if (pkg.toLowerCase().startsWith(input)) {
                    matches.add(pkg);
                }
            }
            return matches;
        }

        return Collections.emptyList();
    }
}