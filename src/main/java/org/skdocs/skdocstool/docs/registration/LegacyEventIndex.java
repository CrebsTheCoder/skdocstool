package org.skdocs.skdocstool.docs.registration;

import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LegacyEventIndex {
    private record EventKey(String name, String syntax, List<String> events, List<String> patterns) {}
    private final Set<EventKey> events = new HashSet<>();

    public void add(String name, String syntax, Collection<String> eventTypes, List<String> patterns) {
        String title = name.startsWith("*") ? name.substring(1) : "On " + name;
        events.add(new EventKey(title, syntax, eventTypes.stream().sorted().toList(),
            patterns.stream().map(BukkitSyntaxInfos::fixPattern).toList()));
    }

    public boolean contains(BukkitSyntaxInfos.Event<?> event) {
        return events.contains(new EventKey(event.name(), event.type().getName(),
            event.events().stream().map(Class::getName).sorted().toList(), List.copyOf(event.patterns())));
    }
}
