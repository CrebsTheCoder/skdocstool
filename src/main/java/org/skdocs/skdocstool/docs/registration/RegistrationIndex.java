package org.skdocs.skdocstool.docs.registration;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RegistrationIndex {
    private record SyntaxKey(Class<?> type, List<String> patterns) {}
    private final Map<SyntaxKey, RegistrationMetadata> syntaxes = new HashMap<>();
    private final Map<Object, RegistrationMetadata> objects = new IdentityHashMap<>();

    public void putSyntax(Class<?> type, String[] patterns, RegistrationMetadata doc) {
        syntaxes.put(new SyntaxKey(type, List.of(patterns)), doc);
    }
    public RegistrationMetadata syntax(Class<?> type, Collection<String> patterns) {
        return syntaxes.get(new SyntaxKey(type, List.copyOf(patterns)));
    }
    public void put(Object key, RegistrationMetadata doc) { objects.put(key, doc); }
    public RegistrationMetadata get(Object key) { return objects.get(key); }
}
