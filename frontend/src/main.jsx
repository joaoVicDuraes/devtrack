import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './index.css';

// Entry point: find the #root element from index.html and mount the React tree.
// StrictMode adds extra development-time checks and warnings; it has no effect
// in a production build.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>
);
