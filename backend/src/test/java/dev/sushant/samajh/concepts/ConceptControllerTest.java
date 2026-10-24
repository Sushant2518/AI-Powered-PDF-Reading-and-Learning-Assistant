package dev.sushant.samajh.concepts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConceptControllerTest {

    @Test
    void slugMergesSpellingVariants() {
        assertEquals("two-phase-commit-2pc", ConceptController.slug("Two-Phase Commit (2PC)"));
        assertEquals("two-phase-commit-2pc", ConceptController.slug("  two phase commit — 2PC "));
        assertEquals("cafe-caching", ConceptController.slug("Café Caching"));
        assertEquals("c-memory-model", ConceptController.slug("C++ memory model"));
    }
}
