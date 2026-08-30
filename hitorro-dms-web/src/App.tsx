import { NavLink, Route, Routes, Navigate } from 'react-router-dom';
import DocumentsPage from './pages/DocumentsPage';
import DocumentDetailPage from './pages/DocumentDetailPage';
import FolderPage from './pages/FolderPage';
import SearchPage from './pages/SearchPage';

export default function App() {
  return (
    <div className="app">
      <aside className="sidebar">
        <h1>Hitorro DMS</h1>
        <nav>
          <NavLink to="/documents">Documents</NavLink>
          <NavLink to="/folders">Folders</NavLink>
          <NavLink to="/search">Search</NavLink>
        </nav>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Navigate to="/documents" replace />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/documents/:id" element={<DocumentDetailPage />} />
          <Route path="/folders" element={<FolderPage />} />
          <Route path="/folders/:folder" element={<FolderPage />} />
          <Route path="/search" element={<SearchPage />} />
        </Routes>
      </main>
    </div>
  );
}
