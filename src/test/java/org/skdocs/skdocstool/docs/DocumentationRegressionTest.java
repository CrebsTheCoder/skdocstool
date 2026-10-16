package org.skdocs.skdocstool.docs;

import org.skdocs.skdocstool.docs.builder.DocumentationJson;

import org.skdocs.skdocstool.docs.registration.RegistrationIndex;

import org.skdocs.skdocstool.docs.registration.RegistrationMetadata;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationRegressionTest {
    private static JsonObject entry(String id, String pattern, String since) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("name", "Same name");
        result.addProperty("since", since);
        JsonArray patterns = new JsonArray();
        patterns.add(pattern);
        result.add("patterns", patterns);
        return result;
    }

    @Test void mergesPatternsWithoutDuplicates() {
        JsonArray entries = new JsonArray();
        entries.add(entry("one", "first", "1.0"));
        entries.add(entry("one", "second", "1.0"));
        entries.add(entry("one", "first", "1.0"));
        JsonArray merged = DocumentationJson.mergeEntries(entries);
        assertEquals(1, merged.size());
        assertEquals(2, merged.get(0).getAsJsonObject().getAsJsonArray("patterns").size());
    }

    @Test void keepsDifferentMetadataEvenWhenNameAndIdMatch() {
        JsonArray entries = new JsonArray();
        entries.add(entry("one", "first", "1.0"));
        entries.add(entry("one", "second", "2.0"));
        assertEquals(2, DocumentationJson.mergeEntries(entries).size());
    }

    @Test void idsAreUniqueAcrossCategoriesAndNaturalSuffixes() {
        JsonArray first = new JsonArray();
        first.add(entry("same", "a", "1"));
        first.add(entry("same-2", "b", "1"));
        JsonArray second = new JsonArray();
        second.add(entry("same", "c", "1"));
        second.add(entry("same-2", "d", "1"));
        DocumentationJson.assignDocIds("Addon", first, second);
        var ids = new HashSet<String>();
        for (JsonArray array : List.of(first, second))
            for (JsonElement element : array)
                assertTrue(ids.add(element.getAsJsonObject().get("doc-id").getAsString()));
    }

    @Test void sharedEventClassKeepsDistinctDocumentation() {
        RegistrationIndex docs = new RegistrationIndex();
        RegistrationMetadata first = doc("First", false);
        RegistrationMetadata second = doc("Second", true);
        docs.putSyntax(String.class, new String[]{"first event"}, first);
        docs.putSyntax(String.class, new String[]{"second event"}, second);
        assertSame(first, docs.syntax(String.class, List.of("first event")));
        assertSame(second, docs.syntax(String.class, List.of("second event")));
        assertNull(docs.syntax(String.class, List.of("unregistered")));
        assertTrue(second.noDoc());
    }

    @Test void functionMetadataUsesRegistrationIdentity() {
        RegistrationIndex docs = new RegistrationIndex();
        Object first = new String("same"), second = new String("same");
        docs.put(first, doc("First", false));
        docs.put(second, doc("Second", false));
        assertEquals("First", docs.get(first).name());
        assertEquals("Second", docs.get(second).name());
    }

    private static RegistrationMetadata doc(String name, boolean hidden) {
        return new RegistrationMetadata("custom-id", name, new String[]{"Description"},
            new String[]{"Example"}, new String[]{"1.0"}, new String[]{"keyword"}, hidden);
    }
}
