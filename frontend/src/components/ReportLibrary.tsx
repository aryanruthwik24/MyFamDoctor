import { Report } from '../types';
import { ReportList } from './ReportList';

type ReportLibraryProps = {
  reports: Report[];
  total: number;
  isUploading: boolean;
  onUpload: () => void;
  onOpen: (report: Report) => void;
  onViewAll: () => void;
  onDelete?: (reportId: number) => void | Promise<void>;
  onDownload?: (report: Report) => void | Promise<void>;
};

export function ReportLibrary({ reports, total, isUploading, onUpload, onOpen, onViewAll, onDelete, onDownload }: ReportLibraryProps) {
  return (
    <div className="reports card">
      <div className="section-title">
        <div><p className="eyebrow">REPORT LIBRARY</p><h2>Your health timeline</h2></div>
      </div>
      <button className="dropzone" onClick={onUpload} disabled={isUploading} aria-busy={isUploading}>
        {isUploading ? <span className="upload-spinner" aria-hidden="true" /> : <span className="upload-icon" aria-hidden="true">↑</span>}
        <strong>{isUploading ? 'Uploading report…' : 'Upload a health report'}</strong>
        <span>{isUploading ? 'Please wait while we process your PDF.' : 'PDF, image, DOCX or TXT · saved privately in this browser'}</span>
      </button>
      <ReportList reports={reports} onOpen={onOpen} onDelete={onDelete} onDownload={onDownload} />
      {total > reports.length && <button className="text-button" onClick={onViewAll}>View all reports →</button>}
    </div>
  );
}
