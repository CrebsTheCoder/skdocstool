package org.skdocs.skdocstool.docs.registration;

public record RegistrationMetadata(String id, String name, String[] description, String[] examples,
                       String[] since, String[] keywords, boolean noDoc) {}
