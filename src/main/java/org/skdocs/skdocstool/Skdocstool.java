package org.skdocs.skdocstool;

import org.bukkit.plugin.java.JavaPlugin;
import org.skdocs.skdocstool.command.GenDocsCommand;
import org.skdocs.skdocstool.command.DocsPreviewCommand;

import java.util.Objects;

public final class Skdocstool extends JavaPlugin {

    @Override
    public void onEnable() {

        Objects.requireNonNull(getCommand("gendocs")).setExecutor(new GenDocsCommand(this));

        DocsPreviewCommand previewCmd = new DocsPreviewCommand(this);
        Objects.requireNonNull(getCommand("docs")).setExecutor(previewCmd);
        Objects.requireNonNull(getCommand("docs")).setTabCompleter(previewCmd);
    }

    @Override
    public void onDisable() {
        getLogger().info("Skdocstool has been disabled");
    }
}