export type Marker = { name:string; value:string; unit?:string; qualifier?:string; referenceRange?:string };
export type ReportLike = { title?:string; reportDate?:string; type?:string; extractedText?:string; biomarkers?:Marker[]; fileName?:string; createdAt?:string; fileUrl?:string; mimeType?:string };

const markerCatalog = [
  { name: 'Hemoglobin', aliases: ['hemoglobin', 'hb'] },
  { name: 'HbA1c', aliases: ['hba1c', 'glycated hemoglobin', 'glycated haemoglobin'] },
  { name: 'Vitamin B12', aliases: ['vitamin b12', 'vit b12', 'b12'] },
  { name: 'Vitamin D', aliases: ['vitamin d', 'vit d'] },
  { name: 'Creatinine', aliases: ['creatinine'] },
  { name: 'eGFR', aliases: ['egfr', 'estimated gfr'] },
  { name: 'Cholesterol', aliases: ['cholesterol'] },
  { name: 'Platelet count', aliases: ['platelet count', 'platelets'] }
];

function formatMarker(marker: Marker) {
  const unit = marker.unit ? ` ${marker.unit}` : '';
  const qualifier = marker.qualifier ? ` (${marker.qualifier})` : '';
  const reference = marker.referenceRange ? ` [ref: ${marker.referenceRange}]` : '';
  return `${marker.name}: ${marker.value}${unit}${qualifier}${reference}`;
}

function findMarker(reports: ReportLike[], query: string) {
  const normalized = query.toLowerCase();
  for (const report of reports) {
    const match = report.biomarkers?.find((marker) => {
      const lowerName = marker.name.toLowerCase();
      return markerCatalog.some((entry) => {
        const nameMatches = entry.name.toLowerCase() === lowerName;
        const aliasMatches = entry.aliases.some((alias) => normalized.includes(alias) || lowerName.includes(alias));
        return nameMatches || aliasMatches;
      });
    });
    if (match) return { report, marker: match };
  }
  return null;
}

export function buildLocalAnswer(question: string, reports: ReportLike[]) {
  const q = question.toLowerCase().trim();
  if (!reports.length) return 'No reports are stored yet. Upload a report first and I can summarize it for you.';

  const latest = reports[0];
  const latestTitle = latest.title || 'Untitled report';
  const latestDate = latest.reportDate || 'an unknown date';

  if (/latest|recent|newest|most recent/.test(q)) {
    return `Your latest report is ${latestTitle} from ${latestDate}.`;
  }

  if (/report|reports|timeline|saved/.test(q)) {
    const titles = reports.map((report) => `${report.title || 'Untitled report'} (${report.reportDate || 'unknown date'})`).join(', ');
    return `You currently have ${reports.length} saved report${reports.length === 1 ? '' : 's'}: ${titles}.`;
  }

  const markerMatch = markerCatalog.find((entry) => entry.aliases.some((alias) => q.includes(alias)));
  if (markerMatch) {
    const match = findMarker(reports, markerMatch.name);
    if (match?.marker) {
      const reportLabel = `${match.report.title || 'Untitled report'} (${match.report.reportDate || 'unknown date'})`;
      return `I found ${formatMarker(match.marker)} in ${reportLabel}.`;
    }

    const extractedText = reports.map((report) => report.extractedText || '').filter(Boolean).join(' ');
    if (extractedText && /hba1c|glycated hemoglobin|glycated haemoglobin|hemoglobin|vitamin b12|vitamin d|creatinine|egfr|cholesterol|platelet/i.test(extractedText)) {
      return `The saved reports mention the requested biomarker, but the report parser did not persist a structured value for it yet. Please open the report preview to verify the extracted text.`;
    }

    const fallbackReport = reports.find((report) => (report.extractedText || '').toLowerCase().includes(markerMatch.name.toLowerCase()) || (report.extractedText || '').toLowerCase().includes(markerMatch.aliases[0]));
    if (fallbackReport) {
      return `I found the report text mentioning ${markerMatch.name}, but the parser did not convert it into a structured biomarker value yet.`;
    }

    return `I could not find a detected value for ${markerMatch.name} in your saved reports yet.`;
  }

  if (/summary|summarize|overview|details/.test(q)) {
    const biomarkerSummary = latest.biomarkers?.length
      ? latest.biomarkers.map((marker) => formatMarker(marker)).join('; ')
      : 'No biomarkers were detected.';
    return `Here is the latest report summary: ${latestTitle} from ${latestDate}. Detected values: ${biomarkerSummary}.`;
  }

  if (latest.biomarkers?.length) {
    const biomarkerSummary = latest.biomarkers.map((marker) => formatMarker(marker)).join('; ');
    return `I’m answering from your saved reports. The latest report is ${latestTitle} from ${latestDate}. Detected values: ${biomarkerSummary}.`;
  }

  return `I’m answering from your saved reports. Your latest report is ${latestTitle} from ${latestDate}.`;
}
