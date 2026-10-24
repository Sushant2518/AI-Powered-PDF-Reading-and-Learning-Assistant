package dev.sushant.samajh.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    private UUID documentId;
    private int pageNumber;
    private String text;
    private boolean needsOcr;

    protected Page() {}

    Page(UUID documentId, int pageNumber, String text, boolean needsOcr) {
        this.documentId = documentId;
        this.pageNumber = pageNumber;
        this.text = text;
        this.needsOcr = needsOcr;
    }

    public UUID getDocumentId() { return documentId; }
    public int getPageNumber() { return pageNumber; }
    public String getText() { return text; }
    public boolean isNeedsOcr() { return needsOcr; }
}
