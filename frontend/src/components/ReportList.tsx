import { useEffect, useState } from 'react';
import { Report } from '../types';

type ReportListProps = {
  reports: Report[];
  isLoading?: boolean;
  onOpen: (report: Report) => void;
  onDelete?: (reportId: number) => void | Promise<void>;
};

export function ReportList({ reports, isLoading = false, onOpen, onDelete }: ReportListProps) {
  if (isLoading) {
    return <LoadingState />;
  }

  if (!reports.length) {
    return <EmptyState />;
  }

  return (
    <div className="timeline">
      {reports.map((report) => <ReportRow key={report.id} report={report} onOpen={onOpen} onDelete={onDelete} />)}
    </div>
  );
}

function LoadingState() {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <span className="upload-spinner" aria-hidden="true" />
      <p>Loading your reports…</p>
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

function ReportRow({ report, onOpen, onDelete }: { report: Report; onOpen: (report: Report) => void; onDelete?: (reportId: number) => void | Promise<void> }) {
  const [isMobile, setIsMobile] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const handleDeleteClick = () => {
    if (!onDelete) {
      return;
    }
    setShowDeleteConfirm(true);
  };

  const confirmDelete = async () => {
    if (!onDelete) {
      return;
    }
    await onDelete(report.id);
    setShowDeleteConfirm(false);
  };

  useEffect(() => {
    const updateViewport = () => setIsMobile(window.innerWidth <= 650);
    updateViewport();
    window.addEventListener('resize', updateViewport);
    return () => window.removeEventListener('resize', updateViewport);
  }, []);

  const truncateText = (value: string, maxLength = 15) => {
    if (!isMobile) return value;
    return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
  };

  const rawTitle = report.title || 'Untitled report';
  const displayTitle = truncateText(rawTitle);
  const rawFileName = report.fileName || 'report.pdf';
  const displayFileName = truncateText(rawFileName);

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
      {onDelete && (
        <>
          <button
            type="button"
            className="delete-button"
            onClick={(event) => {
              event.stopPropagation();
              handleDeleteClick();
            }}
            aria-label={`Delete ${report.title}`}
            title={`Delete ${report.title}`}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5A1.5 1.5 0 0 1 8.5 3h7A1.5 1.5 0 0 1 17 4.5V6h3v2h-1.5v11.5A2.5 2.5 0 0 1 15.99 22H8.01A2.5 2.5 0 0 1 5.5 19.5V8H4V6h3V4.5Zm2 1.5h6V6H9V6Zm-1.5 2v11.5c0 .28.22.5.5.5h7c.28 0 .5-.22.5-.5V8H7.5Zm2.5 2h2v7H10v-7Zm4 0h2v7h-2v-7Z" fill="currentColor"/></svg>
          </button>
          {showDeleteConfirm && (
            <div className="delete-confirm-overlay" onClick={() => setShowDeleteConfirm(false)}>
              <div className="delete-confirm-popover" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true" aria-label="Delete report confirmation">
                <strong>Delete {report.title || report.fileName || 'this report'}</strong>
                <span>Are you sure you want to delete this report?</span>
                <div className="delete-confirm-actions">
                  <button type="button" className="secondary" onClick={() => setShowDeleteConfirm(false)}>Cancel</button>
                  <button type="button" className="danger" onClick={confirmDelete}>Delete</button>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
