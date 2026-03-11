import './App.css'
import './styles/Marketing.css'
import { useEffect, useState } from 'react'
import {
  BrowserRouter as Router,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom'
import Navbar from './components/Navbar'
import Dashboard from './components/Dashboard'
import CatchUp from './components/CatchUp'
import Rules from './components/Rules'
import Settings from './components/Settings'
import LandingPage from './pages/LandingPage'
import SignInPage from './pages/SignInPage'
import SignUpPage from './pages/SignUpPage'
import OnboardingPage from './pages/OnboardingPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import { applyThemeMode, normalizeThemeMode, readStoredThemeMode, storeThemeMode } from './utils/theme'
import { clearAuthSession, readAuthSession, type AuthSession, writeAuthSession } from './utils/session'
import { apiFetch } from './utils/apiClient'

interface LocalAuthPayload {
  email: string
  name: string
  accessToken?: string
  idToken?: string
  tenantId?: string
}

function resolvePostAuthPath(session: AuthSession): string {
  return session.onboardingCompleted ? '/app/dashboard' : '/onboarding'
}

function RequireAuth({ session }: { session: AuthSession }) {
  const location = useLocation()

  if (!session.isAuthenticated) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}

function RequireOnboarding({ session }: { session: AuthSession }) {
  if (!session.onboardingCompleted) {
    return <Navigate to="/onboarding" replace />
  }

  return <Outlet />
}

function AppLayout({ onSignOut }: { onSignOut: () => void }) {
  return (
    <>
      <Navbar onSignOut={onSignOut} />
      <main className="main-content app-main-content">
        <Outlet />
      </main>
    </>
  )
}

function App() {
  const [session, setSession] = useState<AuthSession>(() => readAuthSession())

  const persistSession = (next: AuthSession) => {
    writeAuthSession(next)
    setSession(next)
  }

  const handleLocalSignIn = (payload: LocalAuthPayload) => {
    persistSession({
      isAuthenticated: true,
      onboardingCompleted: false,
      email: payload.email,
      name: payload.name,
      accessToken: payload.accessToken ?? '',
      idToken: payload.idToken ?? '',
      tenantId: payload.tenantId ?? '',
    })
  }

  const handleLocalSignUp = (payload: LocalAuthPayload) => {
    persistSession({
      isAuthenticated: true,
      onboardingCompleted: false,
      email: payload.email,
      name: payload.name,
      accessToken: payload.accessToken ?? '',
      idToken: payload.idToken ?? '',
      tenantId: payload.tenantId ?? '',
    })
  }

  const handleOidcAuthenticated = (authenticatedSession: AuthSession) => {
    persistSession(authenticatedSession)
  }

  const handleCompleteOnboarding = () => {
    persistSession({
      ...session,
      onboardingCompleted: true,
    })
  }

  const handleSignOut = () => {
    clearAuthSession()
    setSession(readAuthSession())
    const storedTheme = readStoredThemeMode()
    applyThemeMode(storedTheme ?? 'light')
  }

  useEffect(() => {
    const storedTheme = readStoredThemeMode()
    applyThemeMode(storedTheme ?? 'light')

    if (!session.isAuthenticated) {
      return
    }

    let active = true
    const loadTenantTheme = async () => {
      try {
        const defaultTenantResponse = await apiFetch('/api/tenants/default')
        if (!defaultTenantResponse.ok) return

        const defaultTenantData = await defaultTenantResponse.json() as { tenantId?: string }
        const tenantId = (defaultTenantData.tenantId ?? '').trim()
        if (tenantId.length === 0) return

        const configResponse = await apiFetch(`/api/tenants/${encodeURIComponent(tenantId)}`)
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
  }, [session.isAuthenticated, session.accessToken])

  const fallbackPath = session.isAuthenticated ? resolvePostAuthPath(session) : '/'

  return (
    <Router>
      <Routes>
        <Route path="/" element={<LandingPage isAuthenticated={session.isAuthenticated} />} />
        <Route
          path="/sign-in"
          element={
            session.isAuthenticated
              ? <Navigate to={resolvePostAuthPath(session)} replace />
              : <SignInPage onSignIn={handleLocalSignIn} />
          }
        />
        <Route
          path="/sign-up"
          element={
            session.isAuthenticated
              ? <Navigate to={resolvePostAuthPath(session)} replace />
              : <SignUpPage onSignUp={handleLocalSignUp} />
          }
        />
        <Route
          path="/auth/callback"
          element={
            session.isAuthenticated
              ? <Navigate to={resolvePostAuthPath(session)} replace />
              : <AuthCallbackPage onAuthenticated={handleOidcAuthenticated} />
          }
        />

        <Route element={<RequireAuth session={session} />}>
          <Route
            path="/onboarding"
            element={
              session.onboardingCompleted
                ? <Navigate to="/app/dashboard" replace />
                : <OnboardingPage userName={session.name} onComplete={handleCompleteOnboarding} onSignOut={handleSignOut} />
            }
          />

          <Route element={<RequireOnboarding session={session} />}>
            <Route path="/app" element={<AppLayout onSignOut={handleSignOut} />}>
              <Route index element={<Navigate to="dashboard" replace />} />
              <Route path="dashboard" element={<Dashboard />} />
              <Route path="catchup" element={<CatchUp />} />
              <Route path="rules" element={<Rules />} />
              <Route path="settings" element={<Settings />} />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<Navigate to={fallbackPath} replace />} />
      </Routes>
    </Router>
  )
}

export default App
