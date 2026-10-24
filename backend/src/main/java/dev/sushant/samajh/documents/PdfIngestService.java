package dev.sushant.samajh.documents;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
class PdfIngestService {

    private static final Logger log = LoggerFactory.getLogger(PdfIngestService.class);

    private final DocumentRepository documents;
    private final PageRepository pages;

    PdfIngestService(DocumentRepository documents, PageRepository pages) {
        this.documents = documents;
        this.pages = pages;
    }

    /** Extracts text page by page. Runs in the background after upload. */
    @Async
    void ingest(UUID documentId, Path file) {
        Document doc = documents.findById(documentId).orElseThrow();
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int count = pdf.getNumberOfPages();
            List<Page> batch = new ArrayList<>();
            for (int n = 1; n <= count; n++) {
                stripper.setStartPage(n);
                stripper.setEndPage(n);
                String text = stripper.getText(pdf).strip();
                batch.add(new Page(documentId, n, text, looksGarbage(text)));
            }
            pages.saveAll(batch);
            doc.markReady(count);
        } catch (Exception e) {
            log.error("Ingest failed for {}", documentId, e);
            doc.markFailed();
        }
        documents.save(doc);
    }

    /**
     * True when extracted text is unusable and the page must be read from its image (OCR, week 6):
     * scanned pages (almost no text) or broken font encodings (replacement / private-use / control chars).
     */
    // ponytail: misses legacy Devanagari fonts (Kruti Dev, Shree-Lipi) whose garbage is valid Latin letters;
    // detect those with a cheap model check when OCR lands.
    static boolean looksGarbage(String text) {
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() < 20) return true;
        long bad = compact.chars().filter(c ->
                c == '�'
                || Character.getType(c) == Character.PRIVATE_USE
                || Character.getType(c) == Character.UNASSIGNED
                || Character.isISOControl(c)).count();
        return bad > compact.length() * 0.3;
    }
}
