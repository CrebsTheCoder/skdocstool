package org.skdocs.skdocstool.docs.addon;

import org.skdocs.skdocstool.docs.registration.RegistrationIndex;
import org.skdocs.skdocstool.docs.registration.LegacyEventIndex;
import org.skriptlang.skript.addon.SkriptAddon;

public record AddonContext(SkriptAddon addon, ClassLoader loader,
                    RegistrationIndex registrationDocs, LegacyEventIndex legacyEvents) {
    public AddonContext(SkriptAddon addon, ClassLoader loader, RegistrationIndex registrationDocs) {
        this(addon, loader, registrationDocs, new LegacyEventIndex());
    }
}
