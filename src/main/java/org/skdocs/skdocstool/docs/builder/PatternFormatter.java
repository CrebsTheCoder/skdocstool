package org.skdocs.skdocstool.docs.builder;

import ch.njol.skript.doc.Documentation;

public final class PatternFormatter extends Documentation {
    private PatternFormatter() {}
    static String format(String pattern) { return cleanPatterns(pattern, false, false); }
}
