package org.skdocs.skdocstool.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.skdocs.skdocstool.docs.GenerateDocs;

public class GenDocsCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public GenDocsCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("NullableToNotNull")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("skdocstool.gendocs")) {
            sender.sendMessage("You don't have permission to use this command.");
            return true;
        }

        GenerateDocs.generate(sender, plugin);
        return true;
    }
}