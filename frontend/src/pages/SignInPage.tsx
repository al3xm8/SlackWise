import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { hasOidcConfig, startOidcFlow } from '../utils/oidc'

interface SignInPayload {
  email: string
  name: string
  accessToken?: string
  idToken?: string
  tenantId?: string
}

interface SignInPageProps {
  onSignIn: (payload: SignInPayload) => void
}

export default function SignInPage({ onSignIn }: SignInPageProps) {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [accessToken, setAccessToken] = useState('')
  const [tenantId, setTenantId] = useState('')
  const [oidcLoading, setOidcLoading] = useState(false)
  const [oidcError, setOidcError] = useState('')

  const oidcEnabled = hasOidcConfig()

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmedEmail = email.trim()
    if (!trimmedEmail || !password.trim()) {
      return
    }

    const fallbackName = trimmedEmail.split('@')[0] ?? 'Operator'
    onSignIn({
      email: trimmedEmail,
      name: fallbackName,
      accessToken: accessToken.trim(),
      tenantId: tenantId.trim(),
    })

    navigate('/onboarding', { replace: true })
  }

  const handleSsoSignIn = async () => {
    setOidcError('')
    setOidcLoading(true)

    try {
      await startOidcFlow('signin')
    } catch (error: unknown) {
      if (error instanceof Error) {
        setOidcError(error.message)
      } else {
        setOidcError('Failed to start secure sign-in')
      }
      setOidcLoading(false)
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card reveal-up">
        <h1>Sign in</h1>
        <p>Use your workspace account to continue.</p>

        {oidcEnabled ? (
          <>
            <button
              type="button"
              className="marketing-button marketing-button-primary auth-submit"
              onClick={handleSsoSignIn}
              disabled={oidcLoading}
            >
              {oidcLoading ? 'Redirecting to SSO...' : 'Continue with SSO'}
            </button>
            {oidcError ? <p className="onboarding-alert onboarding-alert-error">{oidcError}</p> : null}
            <p className="auth-form-note">Or use local development sign-in below.</p>
          </>
        ) : null}

        <form onSubmit={handleSubmit} className="auth-form">
          <label htmlFor="signin-email">Work email</label>
          <input
            id="signin-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="team@company.com"
            required
          />

          <label htmlFor="signin-password">Password</label>
          <input
            id="signin-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="••••••••"
            required
          />

          <details className="auth-advanced">
            <summary>Advanced (optional)</summary>
            <label htmlFor="signin-access-token">Access Token (JWT)</label>
            <textarea
              id="signin-access-token"
              value={accessToken}
              onChange={(event) => setAccessToken(event.target.value)}
              placeholder="Paste bearer token for secured API access"
              rows={3}
            />

            <label htmlFor="signin-tenant-id">Tenant ID override</label>
            <input
              id="signin-tenant-id"
              type="text"
              value={tenantId}
              onChange={(event) => setTenantId(event.target.value)}
              placeholder="Optional local tenant id"
            />
          </details>

          <button type="submit" className="marketing-button marketing-button-secondary auth-submit">Sign in (Local)</button>
        </form>

        <p className="auth-footer">
          No account yet? <Link to="/sign-up">Create one</Link>
        </p>
      </div>
    </div>
  )
}
