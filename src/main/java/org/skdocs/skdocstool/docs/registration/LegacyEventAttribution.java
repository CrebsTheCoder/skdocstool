package org.skdocs.skdocstool.docs.registration;

import ch.njol.skript.lang.SkriptEventInfo;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.docs.Origin;

@SuppressWarnings("removal")
public final class LegacyEventAttribution {
    private static final boolean SUPPORTED = detectSupport();

    private LegacyEventAttribution() {}

    public static boolean supported() { return SUPPORTED; }

    private static boolean detectSupport() {
        try {
            SkriptEventInfo.ModernSkriptEventInfo.class.getConstructor(
                String.class, String[].class, Class.class, String.class, Class[].class, Origin.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static String source(BukkitSyntaxInfos.Event<?> event) {
        return source(event, SUPPORTED);
    }

    static String source(BukkitSyntaxInfos.Event<?> event, boolean supported) {
        if (supported && event instanceof SkriptEventInfo.ModernSkriptEventInfo<?> legacy) {
            String path = legacy.getOriginClassPath();
            if (path != null && !path.isBlank()) return path;
        }
        return event.type().getName();
    }
}
