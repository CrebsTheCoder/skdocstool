package org.skdocs.skdocstool.docs.registration;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.util.SimpleEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "removal"})
class LegacyEventReaderTest {
    @Test void recoversDirectCallsWithoutLoadingTheRegistrationClass() throws Exception {
        LegacyEventIndex index = new LegacyEventIndex();
        String name = getClass().getName() + "$Registrations";
        try (var bytes = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(bytes, "Compiled Registrations test class is missing");
            LegacyEventReader.readClass(bytes.readAllBytes(), index);
        }
        var event = mock(BukkitSyntaxInfos.Event.class);
        when(event.type()).thenReturn(SimpleEvent.class);
        when(event.name()).thenReturn("On Compost");
        when(event.patterns()).thenReturn(List.of("compost"));
        when(event.events()).thenReturn(List.of(BlockBreakEvent.class));
        assertTrue(index.contains(event));
        when(event.patterns()).thenReturn(List.of("different"));
        assertFalse(index.contains(event));
        when(event.name()).thenReturn("Transfer");
        when(event.patterns()).thenReturn(List.of("transfer", "move"));
        when(event.events()).thenReturn(List.of(BlockPlaceEvent.class, BlockBreakEvent.class));
        assertTrue(index.contains(event));
    }

    public static class Registrations {
        static {
            rejectInitialization();
            Skript.registerEvent("Compost", SimpleEvent.class, BlockBreakEvent.class, "compost");
            Skript.registerEvent("*Transfer", SimpleEvent.class,
                new Class[]{BlockPlaceEvent.class, BlockBreakEvent.class}, "transfer", "move");
        }

        private static void rejectInitialization() {
            throw new AssertionError("Registration classes must not be initialized during inspection");
        }
    }
}
