package org.skdocs.skdocstool.docs.element;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import static org.junit.jupiter.api.Assertions.*;

class EventDocumentationTest {
    @Test void unrelatedEventValuesAreRejectedEvenWhenValidationPasses() {
        var value = EventValue.simple(BlockEvent.class, String.class, event -> "block");
        assertEquals(EventValue.Validation.VALID, value.validate(PlayerJoinEvent.class));
        assertFalse(EventDocumentation.appliesTo(value, PlayerJoinEvent.class));
    }

    @Test void inheritedEventValuesAreAccepted() {
        var value = EventValue.simple(BlockEvent.class, String.class, event -> "block");
        assertTrue(EventDocumentation.appliesTo(value, BlockBreakEvent.class));
    }

    @Test void excludedEventsDoNotRunValidationOrEmitSkriptErrors() {
        var value = EventValue.builder(BlockEvent.class, String.class)
            .getter(event -> "block")
            .excludes(BlockBreakEvent.class)
            .excludedErrorMessage("Must not reach Skript's error handler")
            .eventValidator(event -> { throw new AssertionError("Validator should not be called"); })
            .build();
        assertFalse(EventDocumentation.appliesTo(value, BlockBreakEvent.class));
    }
}
