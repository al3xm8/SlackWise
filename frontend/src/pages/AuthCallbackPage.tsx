import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { completeOidcFlow } from '../utils/oidc'
import type { AuthSession } from '../utils/session'

interface AuthCallbackPageProps {
  onAuthenticated: (session: AuthSession) => void
}

export default function AuthCallbackPage({ onAuthenticated }: AuthCallbackPageProps) {
  const navigate = useNavigate()
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let active = true

    const finishLogin = async () => {
      try {
        const resolved = await completeOidcFlow(window.location.href)
        if (!active) {
          return
        }

        onAuthenticated({
          isAuthenticated: true,
          onboardingCompleted: false,
          email: resolved.email,
          name: resolved.name,
          accessToken: resolved.accessToken,
          idToken: resolved.idToken,
          tenantId: resolved.tenantId,
        })

        navigate('/onboarding', { replace: true })
      } catch (error: unknown) {
        if (!active) {
          return
        }

        if (error instanceof Error) {
          setErrorMessage(error.message)
        } else {
          setErrorMessage('Authentication failed during callback processing')
        }
      }
    }

    finishLogin()
    return () => {
      active = false
    }
  }, [navigate, onAuthenticated])

  return (
    <div className="auth-shell">
      <div className="auth-card reveal-up">
        <h1>Signing you in...</h1>
        {errorMessage ? (
          <p className="onboarding-alert onboarding-alert-error">{errorMessage}</p>
        ) : (
          <p>Completing secure authentication with your identity provider.</p>
        )}
      </div>
    </div>
  )
}
