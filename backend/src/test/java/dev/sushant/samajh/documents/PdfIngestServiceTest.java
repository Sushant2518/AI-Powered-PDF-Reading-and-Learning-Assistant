package dev.sushant.samajh.documents;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PdfIngestServiceTest {

    @Test
    void readableTextIsKept() {
        assertFalse(PdfIngestService.looksGarbage("An operation is idempotent if performing it twice has the same effect."));
        assertFalse(PdfIngestService.looksGarbage("एखादी क्रिया एकदा केली काय किंवा अनेकदा केली काय, परिणाम सारखाच राहतो."));
    }

    @Test
    void scannedOrBrokenPagesNeedOcr() {
        assertTrue(PdfIngestService.looksGarbage(""));
        assertTrue(PdfIngestService.looksGarbage("  12  "));
        assertTrue(PdfIngestService.looksGarbage("����� ab �����"));
    }
}
