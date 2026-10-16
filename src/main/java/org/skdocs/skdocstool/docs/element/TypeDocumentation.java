package org.skdocs.skdocstool.docs.element;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.DocumentationIdProvider;
import ch.njol.skript.registrations.Classes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.skdocs.skdocstool.docs.addon.AddonContext;
import org.skdocs.skdocstool.docs.addon.SyntaxOwnership;
import org.skdocs.skdocstool.docs.registration.RegistrationIndex;
import org.skdocs.skdocstool.docs.registration.RegistrationMetadata;
import org.skdocs.skdocstool.docs.builder.DocumentationFormat;

import static org.skdocs.skdocstool.docs.addon.AddonResolver.isOwnedType;
import static org.skdocs.skdocstool.docs.builder.DocumentationJson.addStrings;
import static org.skdocs.skdocstool.docs.builder.DocumentationJson.strings;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.cleanHtml;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.hasContent;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.prefer;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.readableUserPattern;

public final class TypeDocumentation implements DocumentationElement {
    @Override
    public String key() { return "types"; }

    @Override
    public JsonArray collect(AddonContext addon, SyntaxOwnership ownership, DocumentationFormat format) {
        return typesArray(addon.loader(), format == DocumentationFormat.SKRIPT_HUB, addon.registrationDocs());
    }

    private static JsonArray typesArray(ClassLoader loader, boolean skriptHub, RegistrationIndex regDocs) {
        JsonArray arr = new JsonArray();
        for (ClassInfo<?> info : Classes.getClassInfos()) {
            if (!isOwnedType(info, loader)) continue;
            if (ClassInfo.NO_DOC.equals(info.getDocName())) continue;

            RegistrationMetadata rd = regDocs != null ? regDocs.get(info) : null;
            if (rd != null && rd.noDoc()) continue;
            String id = rd != null && rd.id() != null ? rd.id() : DocumentationIdProvider.getId(info);
            if (id == null || id.isBlank()) continue;

            String ciDocName = info.getDocName();
            String name;
            if (ciDocName != null && !ciDocName.isBlank() && !ciDocName.equals(info.getCodeName())) {
                name = ciDocName;
            } else if (rd != null && rd.name() != null && !rd.name().isBlank()) {
                name = rd.name();
            } else {
                name = ciDocName != null ? ciDocName : info.getCodeName();
            }

            String[] description = prefer(cleanHtml(info.getDescription()), rd != null ? rd.description() : null);
            String[] examples    = prefer(cleanHtml(info.getExamples()),    rd != null ? rd.examples()     : null);
            String   since       = info.getSince();
            if ((since == null || since.isBlank()) && rd != null && hasContent(rd.since())) {
                since = String.join(", ", rd.since());
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", name);

            String[] usage = typeUsage(info);
            if (usage != null && usage.length > 0) {
                JsonArray usageArr = new JsonArray();
                for (String u : usage) {
                    if (!u.isBlank()) usageArr.add(u);
                }
                if (!usageArr.isEmpty()) obj.add("usage", usageArr);
            }

            if (skriptHub) {
                addStrings(obj, "description", description);
                addStrings(obj, "examples", examples);
                if (since != null && !since.isBlank()) {
                    JsonArray sa = new JsonArray();
                    sa.add(since);
                    obj.add("since", sa);
                }
                JsonArray patterns = typePatterns(info);
                if (!patterns.isEmpty()) obj.add("patterns", patterns);
                JsonArray changers = typeChangers(info);
                if (!changers.isEmpty()) obj.add("changers", changers);
            } else {
                obj.addProperty("codeName", info.getCodeName());
                obj.add("description", strings(description));
                obj.add("examples", strings(examples));
                obj.addProperty("since", since);
                obj.add("requiredPlugins", strings(info.getRequiredPlugins()));
                obj.addProperty("source", typeSource(info));
            }
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray typeChangers(ClassInfo<?> info) {
        JsonArray arr = new JsonArray();
        Changer<?> changer = info.getChanger();
        if (changer == null) return arr;
        for (Changer.ChangeMode mode : Changer.ChangeMode.values()) {
            try {
                if (changer.acceptChange(mode) != null) {
                    arr.add(mode.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                }
            } catch (Exception ignored) {}
        }
        return arr;
    }

    private static JsonArray typePatterns(ClassInfo<?> info) {
        JsonArray arr = new JsonArray();
        java.util.regex.Pattern[] userPatterns = info.getUserInputPatterns();
        if (userPatterns != null && userPatterns.length > 0) {
            for (java.util.regex.Pattern p : userPatterns) {
                arr.add(readableUserPattern(p.pattern()));
            }
        } else {
            arr.add(info.getCodeName());
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private static <T> String[] typeUsage(ClassInfo<T> info) {

        String[] usage = info.getUsage();
        if (usage != null && usage.length > 0) return usage;
        var supplier = info.getSupplier();
        var parser = (ch.njol.skript.classes.Parser<T>) info.getParser();
        if (supplier == null || parser == null) return null;
        Set<String> values = new LinkedHashSet<>();
        try {
            Iterator<T> iterator = supplier.get();
            while (iterator.hasNext()) {
                T value = iterator.next();
                if (value == null) continue;
                String text = parser.toString(value, 0);
                if (text != null && !text.isBlank()) values.add(text);
            }
        } catch (RuntimeException e) {
            Logger.getLogger("skdocstool").log(java.util.logging.Level.WARNING,
                "Unable to enumerate usage for " + info.getCodeName(), e);
        }
        return values.isEmpty() ? null : values.toArray(String[]::new);
    }

    private static String typeSource(ClassInfo<?> info) {
        for (Object candidate : new Object[]{
                info.getParser(), info.getSerializer(), info.getChanger(),
                info.getCloner(), info.getSupplier()}) {
            if (candidate == null) continue;
            String pkg = candidate.getClass().getPackageName();
            if (pkg.startsWith("org.bukkit") || pkg.startsWith("io.papermc") || pkg.startsWith("net.minecraft")) continue;
            return candidate.getClass().getName();
        }
        return info.getC() != null ? info.getC().getName() : null;
    }

}
