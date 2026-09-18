import { Link, NavLink } from 'react-router-dom';

export default function Layout({ children }) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand">
          Document Processing Pipeline
        </Link>
        <nav className="nav">
          <NavLink to="/">Dashboard</NavLink>
          <NavLink to="/upload">Upload</NavLink>
          <NavLink to="/documents">Documents</NavLink>
        </nav>
      </header>
      <main className="app-main">{children}</main>
    </div>
  );
}
