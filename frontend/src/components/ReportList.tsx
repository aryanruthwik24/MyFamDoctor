import { Report } from '../types';

type ReportListProps = {
  reports: Report[];
  onOpen: (report: Report) => void;
  onDelete?: (reportId: number) => void | Promise<void>;
  onDownload?: (report: Report) => void | Promise<void>;
};

function truncateText(value: string, maxLength = 17): string {
  const cleanValue = value.trim();
  if (cleanValue.length <= maxLength) {
    return cleanValue;
  }
  return `${cleanValue.slice(0, maxLength)}...`;
}

export function ReportList({ reports, onOpen, onDelete, onDownload }: ReportListProps) {
  if (!reports.length) {
    return <EmptyState />;
  }

  return (
    <div className="timeline">
      {reports.map((report) => <ReportRow key={report.id} report={report} onOpen={onOpen} onDelete={onDelete} onDownload={onDownload} />)}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="empty">
      <span className="empty-icon" aria-hidden="true">▤</span>
      <p>No reports yet</p>
      <span>Upload your first CBC, HbA1c, scan, or lab report to begin.</span>
    </div>
  );
}

function ReportRow({ report, onOpen, onDelete, onDownload }: { report: Report; onOpen: (report: Report) => void; onDelete?: (reportId: number) => void | Promise<void>; onDownload?: (report: Report) => void | Promise<void> }) {
  const displayTitle = truncateText(report.title || 'Untitled report', 15);
  const displayFileName = truncateText(report.fileName || 'report.pdf', 15);

  return (
    <div className="report-row">
      <button className="report-row-button" onClick={() => onOpen(report)}>
        <div className="report-icon">
          <span aria-hidden="true">{report.mimeType?.startsWith('image/') ? '▧' : '▤'}</span>
        </div>
        <div className="report-copy">
          <strong>{displayTitle}</strong>
          <p>{displayFileName}</p>
        </div>
        <span className="tag">Open</span>
      </button>
      {onDownload && (
        <button
          type="button"
          className="download-button"
          onClick={(event) => {
            event.stopPropagation();
            onDownload(report);
          }}
          aria-label={`Download ${report.title}`}
          title={`Download ${report.title}`}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.5v9.2l3.3-3.3 1.4 1.4-5.7 5.7-5.7-5.7 1.4-1.4L11 12.7V3.5h1Zm-8 15.5h16v2H4v-2Z" fill="currentColor"/></svg>
        </button>
      )}
      {onDelete && (
        <button
          type="button"
          className="delete-button"
          onClick={(event) => {
            event.stopPropagation();
            onDelete(report.id);
          }}
          aria-label={`Delete ${report.title}`}
          title={`Delete ${report.title}`}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5A1.5 1.5 0 0 1 8.5 3h7A1.5 1.5 0 0 1 17 4.5V6h3v2h-1.5v11.5A2.5 2.5 0 0 1 15.99 22H8.01A2.5 2.5 0 0 1 5.5 19.5V8H4V6h3V4.5Zm2 1.5h6V6H9V6Zm-1.5 2v11.5c0 .28.22.5.5.5h7c.28 0 .5-.22.5-.5V8H7.5Zm2.5 2h2v7H10v-7Zm4 0h2v7h-2v-7Z" fill="currentColor"/></svg>
        </button>
      )}
    </div>
  );
}
