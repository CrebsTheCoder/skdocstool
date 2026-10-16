package org.skdocs.skdocstool.docs.element;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.DocumentationIdProvider;
import ch.njol.skript.registrations.Classes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.skdocs.skdocstool.docs.addon.AddonContext;
import org.skdocs.skdocstool.docs.addon.SyntaxOwnership;
import org.skdocs.skdocstool.docs.registration.RegistrationMetadata;
import org.skdocs.skdocstool.docs.builder.DocumentationFormat;
import org.skriptlang.skript.Skript;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;

import static org.skdocs.skdocstool.docs.builder.DocumentationJson.addStrings;
import static org.skdocs.skdocstool.docs.builder.DocumentationJson.strings;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.cleanPatterns;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.isNoDoc;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.prefer;

public final class EventDocumentation implements DocumentationElement {
    @Override
    public String key() { return "events"; }

    @Override
    public JsonArray collect(AddonContext addon, SyntaxOwnership ownership, DocumentationFormat format) {
        return eventsArray(addon, ownership, format == DocumentationFormat.SKRIPT_HUB);
    }

    private static List<String> eventValueIdentifiers(EventValue<?, ?> ev) {
        Collection<String> explicit = ev.patterns();
        if (explicit != null && !explicit.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (String p : explicit) {
                String c = cleanEventValuePattern(p);
                if (c != null && !c.isBlank()) result.add(c);
            }
            if (!result.isEmpty()) return result;
        }
        return identifiersForValueClass(ev.valueClass());
    }

    private static List<String> identifiersForValueClass(Class<?> valClass) {
        Class<?> type = valClass != null && valClass.isArray() ? valClass.getComponentType() : valClass;

        if (type != null) {
            ClassInfo<?> info = Classes.getExactClassInfo(type);
            if (info != null) {
                return List.of(valClass.isArray() ? info.getName().getPlural() : info.getName().getSingular());
            }
        }

        if (type == null) return List.of();
        return List.of(type.getSimpleName().toLowerCase(Locale.ENGLISH));
    }

    private static String cleanEventValuePattern(String pattern) {
        if (pattern == null) return null;
        String p = pattern.trim();
        p = p.replaceAll("\\[([^\\[\\]]*)\\]", "$1");
        if (p.contains("|")) {
            String first = p.split("\\|", 2)[0].trim();
            if (!first.isBlank()) p = first;
        }
        return p.replaceAll("\\s+", " ").trim();
    }

    private JsonArray eventsArray(AddonContext addon, SyntaxOwnership ownership, boolean skriptHub) {
        JsonArray arr = new JsonArray();
        for (BukkitSyntaxInfos.Event<?> info : addon.addon().syntaxRegistry().syntaxes(BukkitSyntaxInfos.Event.KEY)) {
            Class<?> type = info.type();

            if (!ownership.owns(info, addon)) continue;

            RegistrationMetadata rd = addon.registrationDocs().syntax(type, info.patterns());

            if (rd != null && rd.noDoc()) continue;
            if (isNoDoc(type)) continue;

            String id = rd != null && rd.id() != null ? rd.id() : DocumentationIdProvider.getId(info);
            if (id == null || id.isBlank()) continue;

            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("name", rd != null && rd.name() != null && !rd.name().isBlank() ? rd.name() : info.name());

            String[] desc = rd != null && rd.description() != null && rd.description().length > 0 ? rd.description() : info.description().toArray(new String[0]);
            String[] examples = rd != null && rd.examples() != null && rd.examples().length > 0 ? rd.examples() : info.examples().toArray(new String[0]);
            String[] since = rd != null && rd.since() != null && rd.since().length > 0 ? rd.since() : info.since().toArray(new String[0]);
            String[] keywords = rd != null ? rd.keywords() : null;

            if (!info.patterns().isEmpty()) {
                JsonArray pArr = new JsonArray();
                cleanPatterns(info.patterns()).forEach(p -> pArr.add("[on] " + p));
                obj.add("patterns", pArr);
            }

            if (skriptHub) {
                addStrings(obj, "description", desc);
                addStrings(obj, "examples", examples);
                addStrings(obj, "since", since);
                addStrings(obj, "required plugins", info.requiredPlugins().toArray(new String[0]));
                addStrings(obj, "keywords", prefer(keywords, info.keywords().toArray(String[]::new)));
            } else {
                obj.add("description", strings(desc));
                obj.add("examples", strings(examples));
                obj.add("since", strings(since));
                obj.add("requiredPlugins", strings(info.requiredPlugins()));
                obj.add("keywords", strings(prefer(keywords, info.keywords().toArray(new String[0]))));
                obj.addProperty("source", type.getName());
            }

            String[] eventValues = collectEventValues(info.events());
            if (eventValues != null && eventValues.length > 0) {
                if (skriptHub) {
                    addStrings(obj, "event values", eventValues);
                } else {
                    obj.add("event-values", strings(eventValues));
                }
            }

            arr.add(obj);
        }
        return arr;
    }

    private static String[] collectEventValues(Collection<Class<? extends org.bukkit.event.Event>> eventClasses) {
        if (eventClasses == null || eventClasses.isEmpty()) return null;
        EventValueRegistry registry = ch.njol.skript.Skript.instance().registry(EventValueRegistry.class);
        Set<String> values = new TreeSet<>();
        for (EventValue<?, ?> value : registry.elements()) {

            if (eventClasses.stream().noneMatch(event -> appliesTo(value, event)))
                continue;
            String prefix = switch (value.time()) {
                case PAST -> "past event-";
                case FUTURE -> "future event-";
                default -> "event-";
            };
            for (String identifier : eventValueIdentifiers(value))
                if (!identifier.isBlank()) values.add(prefix + identifier);
        }
        return values.isEmpty() ? null : values.toArray(String[]::new);
    }

    static boolean appliesTo(EventValue<?, ?> value, Class<?> event) {
        return value.eventClass().isAssignableFrom(event)
            && value.excludedEvents().stream().noneMatch(excluded -> excluded.isAssignableFrom(event))
            && value.validate(event) == EventValue.Validation.VALID;
    }
}
