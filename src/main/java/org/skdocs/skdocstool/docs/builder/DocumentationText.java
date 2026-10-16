package org.skdocs.skdocstool.docs.builder;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Keywords;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.NoDoc;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public final class DocumentationText {
    private DocumentationText() {}

    public static boolean hasContent(String[] arr) {
        return arr != null && arr.length > 0;
    }

    public static String[] prefer(String[] primary, String[] fallback) {
        if (primary != null && primary.length > 0) return primary;
        return fallback;
    }

    public static String readableUserPattern(String regex) {
        if (regex == null) return "";
        return regex
                .replaceAll("\\\\([()\\[\\]])", "$1")
                .replaceAll("\\(([^)]+)\\)\\?", "[$1]")
                .replaceAll("(.)\\?", "[$1]")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String cleanHtml(String s) {
        if (s == null) return null;
        return s.replaceAll("<[^>]+>([^<]*)</[^>]+>", "$1")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
    }

    public static String[] cleanHtml(String[] values) {
        if (values == null) return null;
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = cleanHtml(values[i]);
        return out;
    }

    public static String nameOf(Class<?> type) {
        if (type == null) return "Unknown";

        Name ann = type.getAnnotation(Name.class);
        if (ann != null && !ann.value().isBlank()) return ann.value();

        String simpleName = type.getSimpleName();
        String category = extractCategory(simpleName);
        String name = simpleName.replaceFirst("^(Cond|Eff|Expr|Struct|Lit|Sec|On|Simple|Effect|Condition|Expression|Section|Structure)(?=[A-Z])", "");
        name = name.replaceAll("([a-z])([A-Z])", "$1 $2");

        if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        if (!category.isEmpty()) name = category + " - " + name;
        return name;
    }

    public static String extractCategory(String className) {
        if (className.startsWith("Cond")) return "Condition";
        if (className.startsWith("Eff")) return "Effect";
        if (className.startsWith("Expr")) return "Expression";
        if (className.startsWith("Struct")) return "Structure";
        if (className.startsWith("Lit")) return "Literal";
        if (className.startsWith("Sec")) return "Section";
        if (className.startsWith("On") || className.startsWith("Simple")) return "Event";
        return "";
    }

    public static String[] descriptionOf(Class<?> type) {
        Description annotation = type == null ? null : type.getAnnotation(Description.class);
        return annotation == null ? new String[0] : annotation.value();
    }

    public static String[] examplesOf(Class<?> type) {
        if (type == null) return new String[0];
        Examples legacy = type.getAnnotation(Examples.class);
        if (legacy != null) return legacy.value();
        Example.Examples container = type.getAnnotation(Example.Examples.class);
        if (container != null) {
            return Arrays.stream(container.value()).map(Example::value).toArray(String[]::new);
        }
        Example single = type.getAnnotation(Example.class);
        return single != null ? new String[]{single.value()} : new String[0];
    }

    public static String[] sinceOf(Class<?> type) {
        Since annotation = type == null ? null : type.getAnnotation(Since.class);
        return annotation == null ? new String[0] : annotation.value();
    }

    public static String[] requiredPluginsOf(Class<?> type) {
        RequiredPlugins annotation = type == null ? null : type.getAnnotation(RequiredPlugins.class);
        return annotation == null ? new String[0] : annotation.value();
    }

    public static String[] keywordsOf(Class<?> type) {
        Keywords annotation = type == null ? null : type.getAnnotation(Keywords.class);
        return annotation == null ? new String[0] : annotation.value();
    }

    public static boolean isNoDoc(Class<?> type) {
        return type != null && type.isAnnotationPresent(NoDoc.class);
    }

    public static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase(Locale.ROOT);
    }

    public static String cleanPattern(String pattern) {
        return pattern == null ? null : PatternFormatter.format(pattern);
    }

    public static Collection<String> cleanPatterns(Collection<String> patterns) {
        if (patterns == null) return null;
        return patterns.stream().map(DocumentationText::cleanPattern).toList();
    }

}
