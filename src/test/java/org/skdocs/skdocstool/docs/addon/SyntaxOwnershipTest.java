package org.skdocs.skdocstool.docs.addon;

import ch.njol.skript.lang.SkriptEventInfo;
import ch.njol.skript.lang.util.SimpleEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.docs.Origin;
import org.skdocs.skdocstool.docs.registration.RegistrationIndex;
import org.skdocs.skdocstool.docs.registration.RegistrationMetadata;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"removal", "unchecked"})
class SyntaxOwnershipTest {
    private final ClassLoader coreLoader = SimpleEvent.class.getClassLoader();
    private final AddonContext core = addon("Engine", coreLoader);
    private final Class<?> eventClass = eventClass();
    private final AddonContext addon = addon("UnrelatedName", eventClass.getClassLoader());
    private final SyntaxOwnership ownership = new SyntaxOwnership(List.of(core, addon), coreLoader);

    @Test void legacySharedSyntaxBelongsToTheUnderlyingEventProvider() {
        var event = legacyEvent();
        assertTrue(ownership.owns(event, addon));
        assertFalse(ownership.owns(event, core));
    }

    @Test void modernExplicitOriginWinsOverTheEventProvider() {
        var event = mock(BukkitSyntaxInfos.Event.class);
        when(event.type()).thenReturn(SimpleEvent.class);
        when(event.patterns()).thenReturn(List.of("signal"));
        when(event.events()).thenReturn(List.of(eventClass));
        when(event.origin()).thenReturn((Origin.AddonOrigin) () -> core.addon());
        assertSame(core, ownership.owner(event));
    }

    @Test void unknownOriginUsesEventProviderWithoutNameMatching() {
        var event = legacyEvent();
        when(event.origin()).thenReturn(Origin.UNKNOWN);
        assertSame(addon, ownership.owner(event));
    }

    @Test void skrRegistrationOwnsSharedImplementationWithoutCustomEvent() {
        var event = legacyEvent();
        when(event.events()).thenReturn(List.of());
        addon.registrationDocs().putSyntax(SimpleEvent.class, new String[]{"signal"},
            new RegistrationMetadata(null, "Signal", null, null, null, null, false));
        assertSame(addon, ownership.owner(event));
        assertFalse(ownership.owns(event, core));
    }

    @Test void ambiguousEventProvidersAreNotAssignedToCore() {
        var otherClass = eventClass();
        var other = addon("AnotherName", otherClass.getClassLoader());
        var resolver = new SyntaxOwnership(List.of(core, addon, other), coreLoader);
        var event = legacyEvent();
        when(event.events()).thenReturn(List.of(eventClass, otherClass));
        assertNull(resolver.owner(event));
    }

    private SkriptEventInfo.ModernSkriptEventInfo legacyEvent() {
        var event = mock(SkriptEventInfo.ModernSkriptEventInfo.class);
        when(event.type()).thenReturn(SimpleEvent.class);
        when(event.patterns()).thenReturn(List.of("signal"));
        when(event.events()).thenReturn(List.of(eventClass));
        when(event.origin()).thenReturn((Origin.AddonOrigin) () -> core.addon());
        return event;
    }

    private static AddonContext addon(String name, ClassLoader loader) {
        SkriptAddon addon = mock(SkriptAddon.class);
        when(addon.name()).thenReturn(name);
        return new AddonContext(addon, loader, new RegistrationIndex());
    }

    private static Class<?> eventClass() {
        String name = SyntaxOwnershipTest.class.getName() + "$SignalEvent";
        try (var input = SyntaxOwnershipTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(input, "Compiled SignalEvent test class is missing");
            byte[] bytes = input.readAllBytes();
            return new ClassLoader(SyntaxOwnershipTest.class.getClassLoader()) {
                Class<?> define() { return defineClass(name, bytes, 0, bytes.length); }
            }.define();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static class SignalEvent extends Event {
        @Override
        public HandlerList getHandlers() { return new HandlerList(); }
    }
}
