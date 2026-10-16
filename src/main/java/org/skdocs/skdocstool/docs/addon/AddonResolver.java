package org.skdocs.skdocstool.docs.addon;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.function.Function;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.skdocs.skdocstool.docs.registration.RegistrationIndex;
import org.skriptlang.skript.Skript;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.common.function.DefaultFunction;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import static org.skdocs.skdocstool.docs.registration.SkrRegistrationReader.read;

public final class AddonResolver {
    private AddonResolver() {}

    public static Plugin findPluginForAddon(SkriptAddon addon) {
        Plugin p = Bukkit.getPluginManager().getPlugin(addon.name());
        if (p != null) return p;

        ClassLoader syntaxLoader = resolveLoaderFromSyntax(addon);
        if (syntaxLoader != null) {
            for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
                if (pl.getClass().getClassLoader() == syntaxLoader) return pl;
            }
        }

        ClassLoader sourceLoader = addon.source().getClassLoader();
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl.getClass().getClassLoader() == sourceLoader) return pl;
        }

        String addonNameLower = addon.name().toLowerCase(Locale.ROOT);
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl.getPluginMeta().getName().toLowerCase(Locale.ROOT).equals(addonNameLower)) return pl;
        }

        return null;
    }

    public static ClassLoader resolveLoaderFromSyntax(SkriptAddon addon) {
        ClassLoader skriptLoader = ch.njol.skript.Skript.class.getClassLoader();
        SyntaxRegistry registry = addon.syntaxRegistry();
        for (SyntaxRegistry.Key<? extends SyntaxInfo<?>> key : List.of(
                SyntaxRegistry.CONDITION, SyntaxRegistry.EFFECT, SyntaxRegistry.EXPRESSION,
                SyntaxRegistry.SECTION, SyntaxRegistry.STRUCTURE,
                org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos.Event.KEY)) {
            for (SyntaxInfo<?> info : registry.syntaxes(key)) {
                if (!info.origin().name().equalsIgnoreCase(addon.name())) continue;
                ClassLoader cl = info.type().getClassLoader();
                if (cl != null && cl != skriptLoader) return cl;
            }
        }
        return null;
    }

    public static ClassLoader resolveAddonClassLoader(SkriptAddon addon, Plugin owningPlugin) {
        ClassLoader syntaxLoader = resolveLoaderFromSyntax(addon);
        if (syntaxLoader != null) return syntaxLoader;

        if (owningPlugin != null) return owningPlugin.getClass().getClassLoader();
        return addon.source().getClassLoader();
    }

    public static AddonContext createAddonContext(SkriptAddon addon, Plugin owningPlugin) {
        ClassLoader loader = resolveAddonClassLoader(addon, owningPlugin);
        RegistrationIndex registrationDocs = read(owningPlugin);
        return new AddonContext(addon, loader, registrationDocs,
            org.skdocs.skdocstool.docs.registration.LegacyEventReader.read(owningPlugin));
    }

    public static String resolveVersion(SkriptAddon addon) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(addon.name());
        if (plugin != null) return plugin.getPluginMeta().getVersion();
        if (addon.name().equalsIgnoreCase("Skript")) {
            return ch.njol.skript.Skript.getVersion().toString();
        }
        return "unknown";
    }

    public static boolean isOwned(Class<?> type, ClassLoader loader) {
        return type != null && type.getClassLoader() == loader;
    }

    public static boolean isOwnedType(ClassInfo<?> info, ClassLoader loader) {
        Object[] handlers = {
                info.getParser(), info.getSerializer(), info.getChanger(),
                info.getCloner(), info.getSupplier()
        };
        for (Object h : handlers) {
            if (h == null) continue;
            ClassLoader hLoader = h.getClass().getClassLoader();
            if (hLoader != null && hLoader != loader && isPluginClassLoader(hLoader)) return false;
        }
        if (isOwned(info.getC(), loader)) return true;
        return hasLoader(info.getParser(), loader)
                || hasLoader(info.getDefaultExpression(), loader)
                || hasLoader(info.getChanger(), loader)
                || hasLoader(info.getSerializer(), loader)
                || hasLoader(info.getCloner(), loader)
                || hasLoader(info.getSupplier(), loader);
    }

    public static boolean isPluginClassLoader(ClassLoader loader) {
        return loader.getClass().getSimpleName().contains("PluginClassLoader");
    }

    public static boolean isOwnedFunction(Function<?> function, SkriptAddon addon, ClassLoader loader) {
        if (function instanceof DefaultFunction<?> df) return isSameAddon(df.source(), addon);
        return isOwned(function.getClass(), loader);
    }

    public static boolean isSameAddon(SkriptAddon a, SkriptAddon b) {
        if (a == b) return true;
        if (a.name().equalsIgnoreCase(b.name())) return true;
        return false;
    }

    public static boolean hasLoader(Object obj, ClassLoader loader) {
        return obj != null && obj.getClass().getClassLoader() == loader;
    }

}
