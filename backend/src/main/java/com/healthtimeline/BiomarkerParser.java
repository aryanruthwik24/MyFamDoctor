package com.healthtimeline;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

@Component
public class BiomarkerParser {
  private static final Map<BiomarkerName, Pattern> PATTERNS = Map.of(
    BiomarkerName.HEMOGLOBIN, pattern("ha?emoglobin|hae?moglobin|hgb|hb\\b"),
    BiomarkerName.HBA1C, pattern("hba\\s*1c|hbalc|a1c|glycated\\s+ha?emoglobin"),
    BiomarkerName.VITAMIN_B12, pattern("vit(?:amin)?\\s*b\\s*12|cobalamin"),
    BiomarkerName.VITAMIN_D, pattern("vit(?:amin)?\\s*d(?:\\s*(?:25[ -]?(?:oh)?|total))?"),
    BiomarkerName.CREATININE, pattern("(?:serum\\s+)?creatinine"),
    BiomarkerName.EGFR, pattern("e\\s*gfr|estimated\\s+gfr"),
    BiomarkerName.CHOLESTEROL, pattern("(?:total\\s+)?cholesterol"),
    BiomarkerName.PLATELET_COUNT, pattern("platelet(?:\\s+(?:count|cnt))?|platelets?"),
    BiomarkerName.LDL, pattern("ldl|low[- ]density\\s+lipoprotein"),
    BiomarkerName.HDL, pattern("hdl|high[- ]density\\s+lipoprotein"));

  private static Pattern pattern(String marker) {
    return Pattern.compile("(?i)(?:" + marker + ")[^0-9<>]{0,80}([<>≤≥]?)\\s*(\\d+(?:[.,]\\d+)?)\\s*([^0-9\\r\\n;()]{0,25})(?:\\s*[;|(]?\\s*(?:ref(?:erence)?\\s*(?:range)?|normal)?\\s*[:=]?\\s*([<>≤≥]?\\s*\\d+(?:[.,]\\d+)?\\s*(?:-|–|to)\\s*[<>≤≥]?\\s*\\d+(?:[.,]\\d+)?[^\\r\\n;)]{0,20}))?", Pattern.MULTILINE);
  }

  public List<Biomarker> parse(String text, Report report) {
    if (text == null || text.isBlank()) return List.of();
    String normalizedText = normalize(text);
    List<Biomarker> result = new ArrayList<>();
    for (var entry : PATTERNS.entrySet()) {
      Matcher match = entry.getValue().matcher(normalizedText);
      while (match.find()) {
        addBiomarker(result, entry.getKey(), match, report);
      }
    }
    for (var entry : PATTERNS.entrySet()) {
      for (String line : normalizedText.split("\\R")) {
        if (line.isBlank()) continue;
        Matcher match = entry.getValue().matcher(line);
        while (match.find()) {
          addBiomarker(result, entry.getKey(), match, report);
        }
      }
    }
    return deduplicate(result);
  }

  private void addBiomarker(List<Biomarker> target, BiomarkerName name, Matcher match, Report report) {
    try {
      Biomarker b = new Biomarker();
      b.name = name;
      b.qualifier = normalizeQualifier(match.group(1));
      b.value = new BigDecimal(match.group(2).replace(',', '.'));
      b.unit = clean(match.group(3));
      b.referenceRange = clean(match.group(4));
      b.report = report;
      target.add(b);
    } catch (NumberFormatException ignored) {
    }
  }

  private String normalize(String value) {
    return value.replace('\u00a0', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replaceAll("[\\t\\f]+", " ")
        .replaceAll("[ ]{2,}", " ")
        .replaceAll("\\n{2,}", "\n")
        .trim();
  }

  private List<Biomarker> deduplicate(List<Biomarker> biomarkers) {
    LinkedHashMap<String, Biomarker> unique = new LinkedHashMap<>();
    for (Biomarker biomarker : biomarkers) {
      String key = biomarker.name + "|" + biomarker.qualifier + "|" + biomarker.value + "|" + biomarker.unit + "|" + biomarker.referenceRange;
      unique.putIfAbsent(key, biomarker);
    }
    return new ArrayList<>(unique.values());
  }

  private String clean(String value) {
    if (value == null) return null;
    String cleaned = value.trim().replaceAll("\\s+", " ");
    return cleaned.isBlank() ? null : cleaned;
  }

  private String normalizeQualifier(String value) {
    if (value == null || value.isBlank()) return null;
    return value.equals("≤") ? "<=" : value.equals("≥") ? ">=" : value;
  }
}
