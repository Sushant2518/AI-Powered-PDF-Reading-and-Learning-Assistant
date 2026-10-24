package dev.sushant.samajh.documents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes();

    private final DocumentRepository documents;
    private final PageRepository pages;
    private final PdfIngestService ingest;
    private final Path storageDir;

    DocumentController(DocumentRepository documents, PageRepository pages, PdfIngestService ingest,
                       @Value("${samajh.storage-dir}") Path storageDir) throws IOException {
        this.documents = documents;
        this.pages = pages;
        this.ingest = ingest;
        this.storageDir = Files.createDirectories(storageDir);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    Document upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf") || !hasPdfMagic(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }
        UUID id = UUID.randomUUID();
        Path target = storageDir.resolve(id + ".pdf");   // never use the client's filename on disk
        file.transferTo(target);
        Document doc = documents.save(new Document(id, name.substring(0, name.length() - 4), target.getFileName().toString(), file.getSize()));
        ingest.ingest(id, target);
        return doc;
    }

    @GetMapping
    List<Document> list() {
        return documents.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{id}")
    Document get(@PathVariable UUID id) {
        return find(id);
    }

    @GetMapping("/{id}/file")
    ResponseEntity<FileSystemResource> file(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(storageDir.resolve(find(id).getFileName())));
    }

    @GetMapping("/{id}/pages/{n}")
    Page page(@PathVariable UUID id, @PathVariable int n) {
        return pages.findByDocumentIdAndPageNumber(id, n)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    record DocumentPatch(String title, Integer lastPage) {}

    @PatchMapping("/{id}")
    Document patch(@PathVariable UUID id, @RequestBody DocumentPatch patch) {
        Document doc = find(id);
        if (patch.title() != null && !patch.title().isBlank()) doc.setTitle(patch.title().strip());
        if (patch.lastPage() != null && patch.lastPage() >= 1 && patch.lastPage() <= Math.max(1, doc.getPageCount())) {
            doc.setLastPage(patch.lastPage());
        }
        return documents.save(doc);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) throws IOException {
        Document doc = find(id);
        documents.delete(doc);   // pages go with it (ON DELETE CASCADE)
        Files.deleteIfExists(storageDir.resolve(doc.getFileName()));
    }

    private Document find(UUID id) {
        return documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean hasPdfMagic(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return Arrays.equals(in.readNBytes(PDF_MAGIC.length), PDF_MAGIC);
        }
    }
}
