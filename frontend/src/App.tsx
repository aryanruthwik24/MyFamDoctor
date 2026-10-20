import { ChangeEvent, FormEvent, useEffect, useRef, useState } from 'react';
import myFamDoctorLogo from './logo.png';
import { askAboutReports, askGuestAboutReports, deleteReport, downloadReport, fetchReports, uploadReport } from './reportApi';
import { buildLocalAnswer } from './chatAnswers';
import {
  getReportType,
  normalizeReport,
  readFileAsDataUrl,
  sortReports,
} from './reportUtils';
import { Chat } from './components/Chat';
import { DocumentPreview } from './components/DocumentPreview';
import { Header } from './components/Header';
import { ReportLibrary } from './components/ReportLibrary';
import { ReportList } from './components/ReportList';
import { Message, Report, View } from './types';
import { clearAuthSession, getStoredAuthSession, loginUser, registerUser, saveAuthSession, validateAuthSession, AuthSession } from './authApi';
import './styles.css';

const welcomeMessage: Message = {
  id: 'welcome',
  role: 'assistant',
  content: 'Hi, I’m your report companion. Upload reports and ask me about your health timeline.',
};

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => getStoredAuthSession());

  useEffect(() => {
    let isActive = true;

    async function validateSavedSession() {
      const storedSession = getStoredAuthSession();
      if (!storedSession) {
        setSession(null);
        return;
      }

      const validated = await validateAuthSession(storedSession);
      if (!isActive) return;
      if (validated) {
        setSession(validated);
      } else {
        setSession(null);
      }
    }

    validateSavedSession();
    return () => {
      isActive = false;
    };
  }, []);
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [authForm, setAuthForm] = useState({ fullName: '', email: '', password: '' });
  const [authError, setAuthError] = useState('');
  const [isSubmittingAuth, setIsSubmittingAuth] = useState(false);
  const [reports, setReports] = useState<Report[]>([]);
  const [view, setView] = useState<View>('home');
  const [selectedReport, setSelectedReport] = useState<Report | null>(null);
  const [messages, setMessages] = useState<Message[]>([welcomeMessage]);
  const [question, setQuestion] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [isAsking, setIsAsking] = useState(false);
  const [uploadedFileName, setUploadedFileName] = useState('');
  const [error, setError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const saveReports = (nextReports: Report[]) => {
    const ordered = sortReports(nextReports);
    setReports(ordered);

  };

  useEffect(() => {
    if (!session) {
      setReports([]);
      if (view === 'reports') {
        setView('home');
      }
      return;
    }

    setReports([]);
    fetchReports()
      .then(saveReports)
      .catch(() => {
        setReports([]);
        setError('Your account reports could not be loaded. Please sign in again.');
      });
  }, [session, view]);

  async function handleAuthSubmit(event: FormEvent) {
    event.preventDefault();
    const email = authForm.email.trim();
    const password = authForm.password;
    const fullName = authForm.fullName.trim();

    if (!email || !password || (authMode === 'register' && !fullName)) {
      setAuthError('Please fill in all required fields.');
      return;
    }

    setIsSubmittingAuth(true);
    setAuthError('');

    try {
      clearAuthSession();
      setReports([]);

      const nextSession = authMode === 'login'
        ? await loginUser(email, password)
        : await registerUser(fullName, email, password);

      setSession(nextSession);
      setShowAuthModal(false);
      saveAuthSession(nextSession);
    } catch (caughtError) {
      setAuthError(caughtError instanceof Error ? caughtError.message : 'Authentication failed.');
    } finally {
      setIsSubmittingAuth(false);
    }
  }

  async function handleUpload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;

    setError('');
    setIsUploading(true);
    const title = file.name.replace(/\.[^/.]+$/, '');
    const reportDate = new Date().toISOString().slice(0, 10);
    const type = getReportType(file.name);

    try {
      if (!session) {
        const fileUrl = await readFileAsDataUrl(file);
        saveReports([...reports, {
          id: Date.now(), title, reportDate, type, fileName: file.name,
          createdAt: new Date().toISOString(),
          biomarkers: [],
          mimeType: file.type || undefined,
          fileUrl,
          sourceFile: file,
        }]);
        setUploadedFileName(file.name);
        return;
      }

      const report = await uploadReport(file, title, reportDate, type);
      saveReports([...reports, report]);
      setUploadedFileName(file.name);
    } catch (caughtError) {
      const message = caughtError instanceof Error ? caughtError.message : 'The report could not be uploaded.';
      setError(message);
    } finally {
      setIsUploading(false);
      event.target.value = '';
    }
  }

  async function handleAsk(event: FormEvent) {
    event.preventDefault();
    const text = question.trim();
    if (!text || isAsking) return;

    setQuestion('');
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'user', content: text }]);
    setIsAsking(true);

    try {
      const answer = session
        ? await askAboutReports(text, reports)
        : await askGuestAboutReports(text, reports);
      setMessages((current) => [...current, {
        id: crypto.randomUUID(), role: 'assistant', content: answer || buildLocalAnswer(text, reports),
      }]);
    } catch {
      setMessages((current) => [...current, {
        id: crypto.randomUUID(), role: 'assistant', content: buildLocalAnswer(text, reports),
      }]);
    } finally {
      setIsAsking(false);
    }
  }

  const handleLogout = () => {
    clearAuthSession();
    setSelectedReport(null);
    setReports([]);
    setSession(null);
    setMessages([welcomeMessage]);
  };

  const handleDeleteReport = async (reportId: number) => {
    const nextReports = reports.filter((report) => report.id !== reportId);
    saveReports(nextReports);

    if (selectedReport?.id === reportId) {
      setSelectedReport(null);
    }

    try {
      if (session) {
        await deleteReport(reportId);
      }
    } catch {
      saveReports(reports);
      setError('The report could not be deleted.');
    }
  };

  const handleDownloadReport = async (report: Report) => {
    if (report.fileUrl) {
      const link = document.createElement('a');
      link.href = report.fileUrl;
      link.download = report.fileName || `${report.title}.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      return;
    }

    if (!session) {
      setError('Sign in to download saved reports.');
      return;
    }

    try {
      await downloadReport(report.id, report.fileName || `${report.title}.pdf`);
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : 'The report could not be downloaded.');
    }
  };

  const openUploadDialog = () => {
    if (isUploading) {
      return;
    }
    fileInputRef.current?.click();
  };
  const visibleReports = view === 'home' ? reports.slice(0, 4) : reports;

  return (
    <main>
      <Header view={view} reportCount={reports.length} userName={session?.fullName} onNavigate={setView} onOpenAuth={() => setShowAuthModal(true)} onLogout={handleLogout} />
      <input ref={fileInputRef} type="file" accept="application/pdf,.pdf" hidden onChange={handleUpload} />
      {error && <div className="error-banner" role="alert">{error}</div>}

      {view === 'home' ? (
        <>
          <section className="hero">
            <p className="eyebrow">YOUR HEALTH, IN ONE PLACE</p>
            <h1>Understand your health story.</h1>
            <p>Keep every report together and get clear, context-aware answers whenever you need them.</p>
          </section>
          <section className={session ? 'grid' : 'guest-grid'}>
            {session && (
              <ReportLibrary
                reports={visibleReports}
                total={reports.length}
                onUpload={openUploadDialog}
                onOpen={setSelectedReport}
                onViewAll={() => setView('reports')}
                onDelete={handleDeleteReport}
                onDownload={handleDownloadReport}
                isUploading={isUploading}
              />
            )}
            <Chat
              messages={messages}
              question={question}
              setQuestion={setQuestion}
              isAsking={isAsking}
              isUploading={isUploading}
              onSubmit={handleAsk}
              onAddReport={openUploadDialog}
              uploadedFileName={uploadedFileName}
              isGuestMode={!session}
            />
          </section>
        </>
      ) : (
        <section className="reports-page">
          <div className="page-heading">
            <div><p className="eyebrow">REPORT LIBRARY</p><h1>All uploaded reports</h1></div>
            <button className="primary" onClick={openUploadDialog} disabled={isUploading}><span aria-hidden="true">↑</span>{isUploading ? 'Uploading…' : 'Upload report'}</button>
          </div>
          <ReportList reports={visibleReports} onOpen={setSelectedReport} onDelete={handleDeleteReport} onDownload={handleDownloadReport} />
        </section>
      )}

      {selectedReport && <DocumentPreview report={selectedReport} onClose={() => setSelectedReport(null)} onDelete={handleDeleteReport} onDownload={handleDownloadReport} />}

      {showAuthModal && (
        <div className="auth-modal-backdrop" onClick={() => setShowAuthModal(false)}>
          <section className="auth-card" onClick={(event) => event.stopPropagation()}>
            <div className="auth-header-row">
              <div className="auth-brand">
                <span className="brand-mark auth-mark" aria-hidden="true">
                  <img src={myFamDoctorLogo} alt="MyFamDoctor logo" className="brand-logo" />
                </span>
                <div>
                  <strong>MyFamDoctor</strong>
                  <small>Your Personal Family Doctor</small>
                </div>
              </div>
              <button type="button" className="close-auth" onClick={() => setShowAuthModal(false)} aria-label="Close auth modal">×</button>
            </div>

            <div className="auth-toggle" role="tablist" aria-label="Authentication mode">
              <button type="button" className={authMode === 'login' ? 'active' : ''} onClick={() => setAuthMode('login')}>Sign in</button>
              <button type="button" className={authMode === 'register' ? 'active' : ''} onClick={() => setAuthMode('register')}>Register</button>
            </div>

            <form className="auth-form" onSubmit={handleAuthSubmit}>
              {authMode === 'register' && (
                <label>
                  Full name
                  <input
                    type="text"
                    value={authForm.fullName}
                    onChange={(event) => setAuthForm((current) => ({ ...current, fullName: event.target.value }))}
                    placeholder="Jane Doe"
                  />
                </label>
              )}

              <label>
                Email
                <input
                  type="email"
                  value={authForm.email}
                  onChange={(event) => setAuthForm((current) => ({ ...current, email: event.target.value }))}
                  placeholder="you@example.com"
                />
              </label>

              <label>
                Password
                <input
                  type="password"
                  value={authForm.password}
                  onChange={(event) => setAuthForm((current) => ({ ...current, password: event.target.value }))}
                  placeholder="••••••••"
                />
              </label>

              {authError && <div className="auth-error" role="alert">{authError}</div>}

              <button type="submit" className="primary auth-submit" disabled={isSubmittingAuth}>
                {isSubmittingAuth ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Create account'}
              </button>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}
