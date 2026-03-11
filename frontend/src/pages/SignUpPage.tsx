import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { hasOidcConfig, startOidcFlow } from '../utils/oidc'

interface SignUpPayload {
  email: string
  name: string
  accessToken?: string
  idToken?: string
  tenantId?: string
}

interface SignUpPageProps {
  onSignUp: (payload: SignUpPayload) => void
}

export default function SignUpPage({ onSignUp }: SignUpPageProps) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [accessToken, setAccessToken] = useState('')
  const [tenantId, setTenantId] = useState('')
  const [oidcLoading, setOidcLoading] = useState(false)
  const [oidcError, setOidcError] = useState('')

  const oidcEnabled = hasOidcConfig()

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const trimmedName = name.trim()
    const trimmedEmail = email.trim()
    if (!trimmedName || !trimmedEmail || !password.trim()) {
      return
    }

    onSignUp({
      email: trimmedEmail,
      name: trimmedName,
      accessToken: accessToken.trim(),
      tenantId: tenantId.trim(),
    })

    navigate('/onboarding', { replace: true })
  }

  const handleSsoSignUp = async () => {
    setOidcError('')
    setOidcLoading(true)

    try {
      await startOidcFlow('signup')
    } catch (error: unknown) {
      if (error instanceof Error) {
        setOidcError(error.message)
      } else {
        setOidcError('Failed to start secure sign-up')
      }
      setOidcLoading(false)
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card reveal-up">
        <h1>Create your account</h1>
        <p>Start your free workspace and complete setup in a few minutes.</p>

        {oidcEnabled ? (
          <>
            <button
              type="button"
              className="marketing-button marketing-button-primary auth-submit"
              onClick={handleSsoSignUp}
              disabled={oidcLoading}
            >
              {oidcLoading ? 'Redirecting to SSO...' : 'Create account with SSO'}
            </button>
            {oidcError ? <p className="onboarding-alert onboarding-alert-error">{oidcError}</p> : null}
            <p className="auth-form-note">Or use local development sign-up below.</p>
          </>
        ) : null}

        <form onSubmit={handleSubmit} className="auth-form">
          <label htmlFor="signup-name">Full name</label>
          <input
            id="signup-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Jordan Rivera"
            required
          />

          <label htmlFor="signup-email">Work email</label>
          <input
            id="signup-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="team@company.com"
            required
          />

          <label htmlFor="signup-password">Password</label>
          <input
            id="signup-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Create a strong password"
            required
          />

          <details className="auth-advanced">
            <summary>Advanced (optional)</summary>
            <label htmlFor="signup-access-token">Access Token (JWT)</label>
            <textarea
              id="signup-access-token"
              value={accessToken}
              onChange={(event) => setAccessToken(event.target.value)}
              placeholder="Paste bearer token for secured API access"
              rows={3}
            />

            <label htmlFor="signup-tenant-id">Tenant ID override</label>
            <input
              id="signup-tenant-id"
              type="text"
              value={tenantId}
              onChange={(event) => setTenantId(event.target.value)}
              placeholder="Optional local tenant id"
            />
          </details>

          <button type="submit" className="marketing-button marketing-button-secondary auth-submit">Create account (Local)</button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/sign-in">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
