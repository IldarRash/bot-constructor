import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';

import { AuthProvider, useAuth } from './auth/AuthContext';
import { ToastProvider } from './components/Toast';
import './App.css';
import Login from './pages/Login';
import Register from './pages/Register';
import BotList from './pages/BotList';
import BotEditor from './pages/BotEditor';

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading)
    return (
      <div className="app-boot">
        <div className="spinner spinner--lg" />
        <span>Loading your workspace…</span>
      </div>
    );
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <BotList />
              </ProtectedRoute>
            }
          />
          <Route
            path="/bots/new"
            element={
              <ProtectedRoute>
                <BotEditor />
              </ProtectedRoute>
            }
          />
          <Route
            path="/bots/:id"
            element={
              <ProtectedRoute>
                <BotEditor />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
