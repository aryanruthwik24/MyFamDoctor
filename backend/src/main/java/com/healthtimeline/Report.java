package com.healthtimeline;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Report {
    public Long id;
    public String title;
    public String type;
    public LocalDate reportDate;
    public String fileName;
    public String storedFileName;
    public String extractedText;
    public Instant createdAt = Instant.now();
    public List<Biomarker> biomarkers = new ArrayList<>();
}
