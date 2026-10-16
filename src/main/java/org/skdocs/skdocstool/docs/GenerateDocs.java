package org.skdocs.skdocstool.docs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.skdocs.skdocstool.docs.addon.AddonContext;
import org.skdocs.skdocstool.docs.addon.SyntaxOwnership;
import org.skdocs.skdocstool.docs.builder.DocumentationFormat;
import org.skdocs.skdocstool.docs.builder.DocumentationBuilder;
import org.skriptlang.skript.Skript;
import org.skriptlang.skript.addon.SkriptAddon;

import static org.skdocs.skdocstool.docs.addon.AddonResolver.createAddonContext;
import static org.skdocs.skdocstool.docs.addon.AddonResolver.findPluginForAddon;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.sanitize;

public final class GenerateDocs {
    private GenerateDocs() {}

    static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public static void generate(CommandSender sender, JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        Skript skript = ch.njol.skript.Skript.instance();

        List<SkriptAddon> addons = new ArrayList<>(skript.addons());
        if (addons.stream().noneMatch(a -> a.name().equalsIgnoreCase("Skript"))) {
            addons.add(skript);
        }
        addons.sort(Comparator.comparing(a -> a.name().toLowerCase(Locale.ROOT)));

        Path outDir = plugin.getDataFolder().toPath().resolve("documentation");
        try {
            Files.createDirectories(outDir);
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to create output directory: " + e.getMessage(), NamedTextColor.RED));
            log.severe("Failed to create output directory: " + e.getMessage());
            return;
        }

        List<AddonContext> contexts = addons.stream()
            .map(addon -> createAddonContext(addon, findPluginForAddon(addon))).toList();
        SyntaxOwnership ownership = new SyntaxOwnership(contexts, ch.njol.skript.Skript.class.getClassLoader());
        DocumentationBuilder builder = new DocumentationBuilder(ownership);
        int written = 0;
        for (AddonContext context : contexts) {
            SkriptAddon addon = context.addon();
            String safe = sanitize(addon.name());
            try {
                JsonObject skdocs = builder.build(context, DocumentationFormat.SKDOCS);
                Files.writeString(outDir.resolve("skdocs-" + safe + ".json"), GSON.toJson(skdocs));

                Path skriptHubFile = outDir.resolve("skripthub-" + safe + ".json");
                Files.writeString(skriptHubFile, GSON.toJson(builder.build(context, DocumentationFormat.SKRIPT_HUB)));

                written++;
                log.info("Generated docs for " + addon.name());
            } catch (Exception e) {
                sender.sendMessage(Component.text("Failed for " + addon.name() + ": " + e.getMessage(), NamedTextColor.RED));
                log.log(java.util.logging.Level.SEVERE, "Failed generating docs for " + addon.name(), e);
            }
        }

        if (written == 0) {
            sender.sendMessage(Component.text("No docs were generated.", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text(
                    "Generated docs for " + written + " addon(s) in " + outDir, NamedTextColor.GREEN));
        }
    }

}
