package org.skdocs.skdocstool.docs.registration;

import ch.njol.skript.lang.SkriptEventInfo;
import ch.njol.skript.lang.util.SimpleEvent;
import org.junit.jupiter.api.Test;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"removal", "unchecked"})
class LegacyEventAttributionTest {
    @Test void sharedImplementationRetainsEachRegisteringClass() {
        var first = legacy("example.First$Registration");
        var second = legacy("example.Second");
        assertEquals("example.First$Registration", LegacyEventAttribution.source(first, true));
        assertEquals("example.Second", LegacyEventAttribution.source(second, true));
    }

    @Test void missingPathsFallBackToImplementation() {
        for (String path : new String[]{null, "", "  "})
            assertEquals(SimpleEvent.class.getName(), LegacyEventAttribution.source(legacy(path), true));
    }

    @Test void olderSkriptDoesNotTrustLegacyPaths() {
        assertEquals(SimpleEvent.class.getName(),
            LegacyEventAttribution.source(legacy("Addon label"), false));
    }

    @Test void modernEventsKeepImplementationSource() {
        var modern = mock(BukkitSyntaxInfos.Event.class);
        when(modern.type()).thenReturn(SimpleEvent.class);
        assertEquals(SimpleEvent.class.getName(), LegacyEventAttribution.source(modern, true));
    }

    private SkriptEventInfo.ModernSkriptEventInfo<?> legacy(String path) {
        var event = mock(SkriptEventInfo.ModernSkriptEventInfo.class);
        when(event.type()).thenReturn(SimpleEvent.class);
        when(event.getOriginClassPath()).thenReturn(path);
        return event;
    }
}
