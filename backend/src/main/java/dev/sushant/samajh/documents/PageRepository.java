package dev.sushant.samajh.documents;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface PageRepository extends JpaRepository<Page, Long> {
    Optional<Page> findByDocumentIdAndPageNumber(UUID documentId, int pageNumber);

    /** The page, or 404 / 422 when it doesn't exist or has no usable text yet (needs OCR). */
    default Page readable(UUID documentId, int pageNumber) {
        Page page = findByDocumentIdAndPageNumber(documentId, pageNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (page.isNeedsOcr()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "This page has no readable text yet (needs OCR)");
        }
        return page;
    }
}
