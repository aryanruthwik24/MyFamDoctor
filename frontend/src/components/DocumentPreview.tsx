import { useState } from 'react';
import { dateLabel } from '../reportUtils';
import { LabValue, Report } from '../types';

type DocumentPreviewProps = {
  report: Report;
  onClose: () => void;
  onDelete?: (reportId: number) => void | Promise<void>;
  onDownload?: (report: Report) => void | Promise<void>;
};

export function DocumentPreview({ report, onClose, onDelete, onDownload }: DocumentPreviewProps) {
  const preview = getPreview(report);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <section className="document-modal">
        <header className="preview-header">
          <button className="back" onClick={onClose} aria-label="Back to reports">
            <span aria-hidden="true">⟵</span>
            <span>Back</span>
          </button>
          <button className="close" onClick={onClose} aria-label="Close">×</button>
        </header>
        <div className="document-title">
          <div className="report-icon" aria-hidden="true">▤</div>
          <div><p className="eyebrow">{report.type}</p><h2>{report.title}</h2><p>{dateLabel(report.reportDate)} · {report.fileName}</p></div>
          <div className="row-actions">
            {onDownload && (
              <button type="button" className="download-button modal-download" onClick={() => onDownload(report)} aria-label={`Download ${report.title}`} title={`Download ${report.title}`}>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.5v9.2l3.3-3.3 1.4 1.4-5.7 5.7-5.7-5.7 1.4-1.4L11 12.7V3.5h1Zm-8 15.5h16v2H4v-2Z" fill="currentColor"/></svg>
              </button>
            )}
            {onDelete && (
              <button type="button" className="delete-button modal-delete" onClick={() => onDelete(report.id)} aria-label={`Delete ${report.title}`} title={`Delete ${report.title}`}>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5A1.5 1.5 0 0 1 8.5 3h7A1.5 1.5 0 0 1 17 4.5V6h3v2h-1.5v11.5A2.5 2.5 0 0 1 15.99 22H8.01A2.5 2.5 0 0 1 5.5 19.5V8H4V6h3V4.5Zm2 1.5h6V6H9V6Zm-1.5 2v11.5c0 .28.22.5.5.5h7c.28 0 .5-.22.5-.5V8H7.5Zm2.5 2h2v7H10v-7Zm4 0h2v7h-2v-7Z" fill="currentColor"/></svg>
              </button>
            )}
          </div>
        </div>
        {(report.values?.length || report.biomarkers.length > 0) && <MetricList report={report} />}
        {preview}
        {report.fileUrl && <a className="primary open-file" href={report.fileUrl} target="_blank" rel="noreferrer">Open file</a>}
      </section>
    </div>
  );
}

function getMetricStatus(value: LabValue): 'safe' | 'alert' | 'neutral' {
  const lower = Number(value.ref_low ?? Number.NEGATIVE_INFINITY);
  const upper = Number(value.ref_high ?? Number.POSITIVE_INFINITY);
  const numeric = Number(value.value_num ?? Number.NaN);

  if (Number.isFinite(numeric)) {
    if ((Number.isFinite(lower) && numeric < lower) || (Number.isFinite(upper) && numeric > upper)) {
      return 'alert';
    }
    if (value.flag === 'HIGH' || value.flag === 'LOW') {
      return 'alert';
    }
    return 'safe';
  }

  if (value.flag === 'HIGH' || value.flag === 'LOW') {
    return 'alert';
  }

  return 'neutral';
}

function getMetricLabel(value: LabValue): string {
  return (value.test_name || 'Lab value').trim() || 'Lab value';
}

function getMetricValue(value: LabValue): string {
  if (value.value_num != null && Number.isFinite(Number(value.value_num))) {
    const suffix = value.unit ? ` ${value.unit}` : '';
    return `${Number(value.value_num).toString()}${suffix}`;
  }
  return value.value_text || '—';
}

function getMetricRange(value: LabValue): string {
  const hasLow = value.ref_low != null && Number.isFinite(Number(value.ref_low));
  const hasHigh = value.ref_high != null && Number.isFinite(Number(value.ref_high));

  if (hasLow || hasHigh) {
    const low = hasLow ? Number(value.ref_low).toString() : '—';
    const high = hasHigh ? Number(value.ref_high).toString() : '—';
    return `${low} – ${high}`;
  }

  return value.flag ? value.flag : 'Normal range not provided';
}

function getMetricSummary(value: LabValue): string {
  return (value.summary ?? value.test_name_summary ?? value.value_summary ?? '').trim();
}

function MetricList({ report }: { report: Report }) {
  const [selectedValue, setSelectedValue] = useState<LabValue | null>(null);

  const values = report.values?.length ? report.values : report.biomarkers.map((marker) => ({
    test_name: marker.name,
    value_text: marker.value,
    unit: marker.unit ?? undefined,
    flag: marker.qualifier ?? undefined,
    ref_low: undefined,
    ref_high: undefined,
    summary: undefined,
    test_name_summary: undefined,
    value_summary: undefined,
  } satisfies LabValue));

  const selectedSummary = selectedValue ? getMetricSummary(selectedValue) : '';

  return (
    <section className="biomarkers">
      <h3>Lab values</h3>
      <div className="metric-grid">
        {values.map((value, index) => {
          const status = getMetricStatus(value);
          const key = `${getMetricLabel(value)}-${index}`;
          const summaryText = getMetricSummary(value);

          return (
            <button
              type="button"
              key={key}
              className={`metric-card ${status}`}
              onClick={() => summaryText && setSelectedValue(value)}
              aria-expanded={false}
            >
              <span>{getMetricLabel(value)}</span>
              <strong>{getMetricValue(value)}</strong>
              <small>Normal range: {getMetricRange(value)}</small>
            </button>
          );
        })}
      </div>

      {selectedValue && selectedSummary && (
        <div className="metric-summary-overlay" role="dialog" aria-modal="true" aria-label={`Summary for ${getMetricLabel(selectedValue)}`}>
          <div className="metric-summary-panel">
            <header className="metric-summary-header">
              <button type="button" className="back metric-summary-back" onClick={() => setSelectedValue(null)} aria-label="Back to lab values">
                <span aria-hidden="true">⟵</span>
                <span>Back</span>
              </button>
              <button type="button" className="close metric-summary-close" onClick={() => setSelectedValue(null)} aria-label="Close summary">×</button>
            </header>
            <div className="metric-summary-body">
              <div className="metric-summary-topline">
                <div className="metric-summary-heading">
                  <span className="metric-summary-label">{getMetricLabel(selectedValue)}</span>
                  <span className="metric-summary-range">Normal range: {getMetricRange(selectedValue)}</span>
                </div>
                <strong>{getMetricValue(selectedValue)}</strong>
              </div>
              <div className="metric-summary-text">
                {selectedSummary.split(/\n+/).filter(Boolean).map((paragraph, index) => (
                  <p key={`${getMetricLabel(selectedValue)}-${index}`}>{paragraph}</p>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function getPreview(report: Report) {
  if (report.mimeType?.startsWith('image/') && report.fileUrl) {
    return <img src={report.fileUrl} alt={report.title} />;
  }
  if (report.mimeType === 'application/pdf' && report.fileUrl) {
    return <iframe src={report.fileUrl} title={report.title} />;
  }
  return null;
}

function BiomarkerList({ report }: { report: Report }) {
  return (
    <section className="biomarkers">
      <h3>Detected biomarkers</h3>
      <div>
        {report.biomarkers.map((marker) => (
          <article key={marker.name}>
            <span>{marker.name}</span>
            <strong>{marker.value}{marker.unit ? ` ${marker.unit}` : ''}</strong>
            <small>{marker.referenceRange ? `Reference: ${marker.referenceRange}` : ''}</small>
          </article>
        ))}
      </div>
    </section>
  );
}
