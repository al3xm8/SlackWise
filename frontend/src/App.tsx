import './App.css'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import Dashboard from './components/Dashboard'
import CatchUp from './components/CatchUp'
import Rules from './components/Rules'
import Settings from './components/Settings'

function App() {
  return (
    <Router>
      <Navbar />
      <main className="main-content">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/catchup" element={<CatchUp />} />
          <Route path="/rules" element={<Rules />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </main>
    </Router>
  )
}

export default App
