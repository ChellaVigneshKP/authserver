import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import ClientList from './pages/ClientList';
import ClientDetail from './pages/ClientDetail';
import ClientForm from './pages/ClientForm';
import './App.css';

function App() {
  return (
    <Router>
      <div className="App">
        <header className="app-header">
          <h1>🔐 OAuth2 Client Management</h1>
        </header>
        
        <div className="container">
          <Routes>
            <Route path="/" element={<Navigate to="/clients" replace />} />
            <Route path="/clients" element={<ClientList />} />
            <Route path="/clients/create" element={<ClientForm />} />
            <Route path="/clients/:id" element={<ClientDetail />} />
            <Route path="/clients/:id/edit" element={<ClientForm />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}

export default App;
