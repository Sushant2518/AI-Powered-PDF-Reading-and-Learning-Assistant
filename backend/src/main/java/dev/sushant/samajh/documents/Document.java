package dev.sushant.samajh.documents;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    public enum Status { PROCESSING, READY, FAILED }

    @Id
    private UUID id;
    private String title;
    private String fileName;
    private long sizeBytes;
    private int pageCount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int lastPage = 1;
    private Instant createdAt = Instant.now();

    protected Document() {}

    Document(UUID id, String title, String fileName, long sizeBytes) {
        this.id = id;
        this.title = title;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.status = Status.PROCESSING;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public long getSizeBytes() { return sizeBytes; }
    public int getPageCount() { return pageCount; }
    public Status getStatus() { return status; }
    public int getLastPage() { return lastPage; }
    public Instant getCreatedAt() { return createdAt; }

    void setTitle(String title) { this.title = title; }
    void setLastPage(int lastPage) { this.lastPage = lastPage; }

    void markReady(int pageCount) {
        this.pageCount = pageCount;
        this.status = Status.READY;
    }

    void markFailed() { this.status = Status.FAILED; }
}
