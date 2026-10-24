package dev.sushant.samajh.ai;

import dev.sushant.samajh.documents.Document;
import dev.sushant.samajh.documents.DocumentRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads a page from its image: for scanned pages, and for old-font Marathi/Hindi PDFs (Kruti Dev, Shree-Lipi)
 * whose extracted text is gibberish. Replaces the page text and clears that page's cached AI results.
 */
@RestController
class OcrController {

    private final AiService ai;
    private final DocumentRepository documents;
    private final JdbcClient jdbc;
    private final Path storageDir;

    OcrController(AiService ai, DocumentRepository documents, JdbcClient jdbc, @Value("${samajh.storage-dir}") Path storageDir) {
        this.ai = ai;
        this.documents = documents;
        this.jdbc = jdbc;
        this.storageDir = storageDir;
    }

    @PostMapping("/api/documents/{id}/pages/{n}/ocr")
    Map<String, Object> ocr(@PathVariable UUID id, @PathVariable int n) throws IOException {
        Document doc = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (n < 1 || n > doc.getPageCount()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        String text;
        try {
            text = ai.ocr(renderPage(storageDir.resolve(doc.getFileName()), n));
        } catch (AiService.UserFacingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
        if (text.isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "No text found on this page image");

        jdbc.sql("UPDATE pages SET text = ?, needs_ocr = false, ocr = true, concepts_extracted = false WHERE document_id = ? AND page_number = ?")
                .params(text, id, n).update();
        jdbc.sql("DELETE FROM page_ai WHERE document_id = ? AND page_number = ?").params(id, n).update();
        return Map.of("pageNumber", n, "text", text, "needsOcr", false);
    }

    /** 150 DPI is plenty for reading text and keeps the image small (fewer tokens). */
    static byte[] renderPage(Path pdf, int page) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            BufferedImage image = new PDFRenderer(doc).renderImageWithDPI(page - 1, 150);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return png.toByteArray();
        }
    }
}
