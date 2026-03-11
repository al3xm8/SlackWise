export interface AuthSession {
  isAuthenticated: boolean
  onboardingCompleted: boolean
  email: string
  name: string
  accessToken: string
  idToken: string
  tenantId: string
}

const AUTH_SESSION_KEY = 'slackwise.auth.session'

const EMPTY_SESSION: AuthSession = {
  isAuthenticated: false,
  onboardingCompleted: false,
  email: '',
  name: '',
  accessToken: '',
  idToken: '',
  tenantId: '',
}

export function readAuthSession(): AuthSession {
  if (typeof window === 'undefined') {
    return EMPTY_SESSION
  }

  const raw = window.localStorage.getItem(AUTH_SESSION_KEY)
  if (!raw) {
    return EMPTY_SESSION
  }

  try {
    const parsed = JSON.parse(raw) as Partial<AuthSession>
    return {
      isAuthenticated: Boolean(parsed.isAuthenticated),
      onboardingCompleted: Boolean(parsed.onboardingCompleted),
      email: typeof parsed.email === 'string' ? parsed.email : '',
      name: typeof parsed.name === 'string' ? parsed.name : '',
      accessToken: typeof parsed.accessToken === 'string' ? parsed.accessToken : '',
      idToken: typeof parsed.idToken === 'string' ? parsed.idToken : '',
      tenantId: typeof parsed.tenantId === 'string' ? parsed.tenantId : '',
    }
  } catch {
    return EMPTY_SESSION
  }
}

export function writeAuthSession(session: AuthSession): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session))
}

export function clearAuthSession(): void {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(AUTH_SESSION_KEY)
}
