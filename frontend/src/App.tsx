import './App.css'
import { useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import Dashboard from './components/Dashboard'
import CatchUp from './components/CatchUp'
import Rules from './components/Rules'
import Settings from './components/Settings'
import { applyThemeMode, normalizeThemeMode, readStoredThemeMode, storeThemeMode } from './utils/theme'

function App() {
  useEffect(() => {
    const storedTheme = readStoredThemeMode()
    applyThemeMode(storedTheme ?? 'light')

    let active = true
    const loadTenantTheme = async () => {
      try {
        const defaultTenantResponse = await fetch('/api/tenants/default')
        if (!defaultTenantResponse.ok) return

        const defaultTenantData = await defaultTenantResponse.json() as { tenantId?: string }
        const tenantId = (defaultTenantData.tenantId ?? '').trim()
        if (tenantId.length === 0) return

        const configResponse = await fetch(`/api/tenants/${encodeURIComponent(tenantId)}`)
        if (!configResponse.ok) return

        const configData = await configResponse.json() as { themeMode?: string }
        const tenantTheme = normalizeThemeMode(configData.themeMode)
        if (active) {
          applyThemeMode(tenantTheme)
          storeThemeMode(tenantTheme)
        }
      } catch {
        // Keep the locally stored theme if backend theme lookup fails.
      }
    }

    loadTenantTheme()
    return () => {
      active = false
    }
  }, [])

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
