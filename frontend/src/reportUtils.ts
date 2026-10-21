import { LabValue, Marker, Report } from './types';

export const REPORT_STORAGE_KEY = 'healthtimeline-reports';

const markerDefinitions = [
  { name: 'Hemoglobin', match: /ha?emoglobin|\bhb\b/i },
  { name: 'HbA1c', match: /hba\s*1c|glycated\s+ha?emoglobin/i },
  { name: 'Vitamin B12', match: /vit(?:amin)?\s*b\s*12|cobalamin/i },
  { name: 'Vitamin D', match: /vit(?:amin)?\s*d/i },
  { name: 'Creatinine', match: /creatinine/i },
  { name: 'eGFR', match: /e\s*gfr|estimated\s+gfr/i },
  { name: 'Cholesterol', match: /cholesterol/i },
  { name: 'Platelet count', match: /platelet(?:\s+count)?/i },
];

export function sortReports(reports: Report[]): Report[] {
  return [...reports].sort((a, b) => b.reportDate.localeCompare(a.reportDate));
}

function parseValuesField(rawValue: unknown): LabValue[] {
  if (Array.isArray(rawValue)) {
    return rawValue.filter((item) => item && typeof item === 'object').map((item) => {
      const value = item as Record<string, unknown>;
      const summaryValue = value.summary ?? value.test_name_summary ?? value.value_summary ?? '';
      return {
        ...value,
        summary: String(summaryValue),
      } as LabValue;
    });
  }

  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    return [];
  }

  try {
    const parsed = JSON.parse(rawValue) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }

    return parsed.filter((item) => item && typeof item === 'object').map((item) => {
      const value = item as Record<string, unknown>;
      const summaryValue = value.summary ?? value.test_name_summary ?? value.value_summary ?? '';
      return {
        ...value,
        summary: String(summaryValue),
      } as LabValue;
    });
  } catch {
    return [];
  }
}

export function normalizeReport(raw: unknown): Report {
  const value = raw as Record<string, unknown>;
  const biomarkers = Array.isArray(value.biomarkers)
    ? value.biomarkers.map((item) => {
        const marker = item as Record<string, unknown>;
        return {
          name: String(marker.name ?? ''),
          value: String(marker.value ?? ''),
          unit: marker.unit ? String(marker.unit) : undefined,
          qualifier: marker.qualifier ? String(marker.qualifier) : undefined,
          referenceRange: marker.referenceRange ? String(marker.referenceRange) : undefined,
        };
      })
    : [];

  return {
    id: Number(value.id),
    title: String(value.title ?? 'Untitled report'),
    reportDate: String(value.reportDate ?? ''),
    type: String(value.type ?? 'Health report'),
    fileName: String(value.fileName ?? ''),
    createdAt: String(value.createdAt ?? new Date().toISOString()),
    extractedText: value.extractedText ? String(value.extractedText) : undefined,
    fileUrl: value.fileUrl ? String(value.fileUrl) : undefined,
    mimeType: value.mimeType ? String(value.mimeType) : undefined,
    biomarkers,
    values: parseValuesField(value.values),
  };
}

export function extractBiomarkers(text: string): Marker[] {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);

  return markerDefinitions.flatMap(({ name, match }) => {
    const matchingLines = lines.filter((candidate) => match.test(candidate));
    if (!matchingLines.length) return [];

    const firstLine = matchingLines[0];
    const valueMatch = firstLine.match(/([<>≤≥]?)\s*(\d+(?:[.,]\d+)?)(?:\s*([%a-zA-Zµμ/^0-9.-]+))?/);
    if (!valueMatch) return [];

    const rangeMatch = firstLine.match(
      /(?:ref(?:erence)?(?: range)?|normal)[:=]?\s*([<>≤≥]?\s*\d+(?:[.,]\d+)?\s*(?:-|–|to)\s*[<>≤≥]?\s*\d+(?:[.,]\d+)?)/i,
    );
    const qualifier = valueMatch[1] || undefined;

    return [
      {
        name,
        value: `${qualifier ?? ''}${valueMatch[2]}`,
        unit: valueMatch[3]?.trim() || undefined,
        qualifier,
        referenceRange: rangeMatch?.[1]?.trim(),
      },
    ];
  });
}

export function getReportType(fileName: string): string {
  if (/hba1c/i.test(fileName)) return 'HbA1c test';
  if (/cbc|cbp/i.test(fileName)) return 'CBC / CBP test';
  if (/scan/i.test(fileName)) return 'Body scan';
  return 'Health report';
}

export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export function dateLabel(date: string): string {
  return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}
