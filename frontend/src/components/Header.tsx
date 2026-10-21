import { View } from '../types';
import myFamDoctorLogo from '../logo.png';

type HeaderProps = {
  view: View;
  reportCount: number;
  isLoadingReports?: boolean;
  userName?: string;
  onNavigate: (view: View) => void;
  onOpenAuth?: () => void;
  onLogout?: () => void;
};

export function Header({ view, reportCount, isLoadingReports = false, userName, onNavigate, onOpenAuth, onLogout }: HeaderProps) {
  const initials = userName ? userName.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? '').join('') : 'AR';

  return (
    <header>
      <button className="brand" onClick={() => onNavigate('home')}>
        <span className="brand-mark" aria-hidden="true">
          <img src={myFamDoctorLogo} alt="MyFamDoctor logo" className="brand-logo" />
        </span>
        <div>MyFamDoctor<small>Your Personal Family Doctor</small></div>
      </button>
      {userName && (
        <nav>
          <button className={view === 'home' ? 'active' : ''} onClick={() => onNavigate('home')}>Overview</button>
          <button className={view === 'reports' ? 'active' : ''} onClick={() => onNavigate('reports')}>
            Reports{!isLoadingReports && <b>{reportCount}</b>}
          </button>
        </nav>
      )}
      <div className="header-actions">
        {userName ? (
          <>
            <button className="profile" aria-label="Profile">{initials}</button>
            {onLogout && (
              <button className="logout-button" type="button" onClick={onLogout}>Logout</button>
            )}
          </>
        ) : (
          <button className="sign-in-button" type="button" onClick={onOpenAuth}>Sign in</button>
        )}
      </div>
    </header>
  );
}
