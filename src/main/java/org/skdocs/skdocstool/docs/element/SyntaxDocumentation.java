package org.skdocs.skdocstool.docs.element;

import ch.njol.skript.doc.DocumentationIdProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import org.skdocs.skdocstool.docs.addon.AddonContext;
import org.skdocs.skdocstool.docs.addon.SyntaxOwnership;
import org.skdocs.skdocstool.docs.registration.RegistrationMetadata;
import org.skdocs.skdocstool.docs.builder.DocumentationFormat;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import static org.skdocs.skdocstool.docs.builder.DocumentationJson.addStrings;
import static org.skdocs.skdocstool.docs.builder.DocumentationJson.strings;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.cleanPatterns;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.descriptionOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.examplesOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.isNoDoc;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.keywordsOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.nameOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.prefer;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.requiredPluginsOf;
import static org.skdocs.skdocstool.docs.builder.DocumentationText.sinceOf;

public final class SyntaxDocumentation<I extends SyntaxInfo<?>> implements DocumentationElement {
    private final String key;
    private final SyntaxRegistry.Key<I> registryKey;

    public SyntaxDocumentation(String key, SyntaxRegistry.Key<I> registryKey) {
        this.key = key;
        this.registryKey = registryKey;
    }

    @Override
    public String key() { return key; }

    @Override
    public JsonArray collect(AddonContext addon, SyntaxOwnership ownership, DocumentationFormat format) {
        JsonArray arr = new JsonArray();
        for (I info : addon.addon().syntaxRegistry().syntaxes(registryKey)) {
            Class<?> type = info.type();
            if (!ownership.owns(info, addon)) continue;
            RegistrationMetadata rd = addon.registrationDocs().syntax(type, info.patterns());
            if (rd == null && info instanceof ch.njol.skript.doc.Documentable doc) {
                rd = new RegistrationMetadata(null, doc.name(), doc.description().toArray(String[]::new),
                    doc.examples().toArray(String[]::new), doc.since().toArray(String[]::new),
                    doc.keywords().toArray(String[]::new), false);
            }
            if (rd != null && rd.noDoc()) continue;
            if (isNoDoc(type)) continue;
            JsonObject obj = buildEntry(rd != null && rd.id() != null ? rd.id() : DocumentationIdProvider.getId(info), type, info.patterns(), rd, format);
            if (obj != null) arr.add(obj);
        }
        return arr;
    }

    private JsonObject buildEntry(String id, Class<?> type, Collection<String> patterns,
                                   RegistrationMetadata metadata, DocumentationFormat format) {
        if (id == null || id.isBlank()) return null;
        String name = metadata != null && metadata.name() != null && !metadata.name().isBlank()
            ? metadata.name() : nameOf(type);
        if (name == null || name.isBlank()) return null;

        String[] description = prefer(metadata != null ? metadata.description() : null, descriptionOf(type));
        String[] examples = prefer(metadata != null ? metadata.examples() : null, examplesOf(type));
        String[] since = prefer(metadata != null ? metadata.since() : null, sinceOf(type));
        String[] keywords = prefer(metadata != null ? metadata.keywords() : null, keywordsOf(type));
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("name", name);
        entry.add("patterns", strings(cleanPatterns(patterns)));

        if (format == DocumentationFormat.SKRIPT_HUB) {
            addStrings(entry, "description", description);
            addStrings(entry, "examples", examples);
            addStrings(entry, "since", since);
            addStrings(entry, "keywords", keywords);
            addStrings(entry, "required plugins", requiredPluginsOf(type));
        } else {
            entry.add("description", strings(description));
            entry.add("examples", strings(examples));
            entry.add("since", strings(since));
            entry.add("keywords", strings(keywords));
            entry.add("requiredPlugins", strings(requiredPluginsOf(type)));
            entry.addProperty("source", type.getName());
        }
        return entry;
    }
}
