package com.healthtimeline;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.LocalDateTime;

@Entity
@Access(AccessType.FIELD)
@Table(name = "health_reports")
public class HealthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_type", nullable = false)
    private String contentType = "application/pdf";

    // Bind this value as binary data. @Lob makes Hibernate use PostgreSQL's
    // large-object OID path (a bigint), which is incompatible with BYTEA.
    @JdbcTypeCode(Types.LONGVARBINARY)
    @Column(name = "data", nullable = false, columnDefinition = "BYTEA")
    private byte[] data;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "report_date")
    private java.time.LocalDate reportDate;

    @Column(name = "report_type")
    private String reportType;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "\"values\"", columnDefinition = "TEXT")
    private String values;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public HealthReport() {
    }

    public HealthReport(Long userId, String fileName, String contentType, Long fileSize, byte[] data) {
        this.userId = userId;
        this.fileName = fileName;
        this.contentType = contentType == null || contentType.isBlank() ? "application/pdf" : contentType;
        this.fileSize = fileSize;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType == null || contentType.isBlank() ? "application/pdf" : contentType;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public java.time.LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(java.time.LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getValues() {
        return values;
    }

    public void setValues(String values) {
        this.values = values;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
}
