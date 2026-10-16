package org.skdocs.skdocstool.docs.addon;

import ch.njol.skript.lang.SyntaxElementInfo;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.docs.Origin;
import org.skriptlang.skript.registration.SyntaxInfo;

public final class SyntaxOwnership {
    private final List<AddonContext> addons;
    private final ClassLoader coreLoader;

    public SyntaxOwnership(List<AddonContext> addons, ClassLoader coreLoader) {
        this.addons = List.copyOf(addons);
        this.coreLoader = coreLoader;
    }

    public boolean owns(SyntaxInfo<?> syntax, AddonContext addon) {
        return addon == owner(syntax);
    }

    @SuppressWarnings("removal")
    public AddonContext owner(SyntaxInfo<?> syntax) {
        Set<AddonContext> registered = new LinkedHashSet<>();
        for (AddonContext addon : addons)
            if (addon.registrationDocs().syntax(syntax.type(), syntax.patterns()) != null)
                registered.add(addon);
        if (registered.size() == 1) return registered.iterator().next();

        AddonContext declared = null;
        if (syntax.origin() instanceof Origin.AddonOrigin origin) {
            declared = addons.stream().filter(a -> a.addon().name().equals(origin.addon().name()))
                .findFirst().orElse(null);
        }
        if (!(syntax instanceof SyntaxElementInfo<?>) && declared != null) return declared;

        if (syntax instanceof BukkitSyntaxInfos.Event<?> event) {
            List<AddonContext> callers = addons.stream().filter(a -> a.legacyEvents().contains(event)).toList();
            if (callers.size() == 1) return callers.getFirst();
            if (callers.size() > 1) return null;
        }
        AddonContext implementation = ownerOf(syntax.type());
        if (implementation != null && implementation.loader() != coreLoader) return implementation;
        if (syntax instanceof BukkitSyntaxInfos.Event<?> event) {
            Set<AddonContext> eventOwners = new LinkedHashSet<>();
            for (Class<?> eventType : event.events()) {
                AddonContext owner = ownerOf(eventType);
                if (owner != null && owner.loader() != coreLoader) eventOwners.add(owner);
            }
            if (eventOwners.size() == 1) return eventOwners.iterator().next();
            if (eventOwners.size() > 1) return null;
        }
        return declared != null ? declared : implementation;
    }

    private AddonContext ownerOf(Class<?> type) {
        List<AddonContext> matches = addons.stream()
            .filter(a -> a.loader() == type.getClassLoader()).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
}
