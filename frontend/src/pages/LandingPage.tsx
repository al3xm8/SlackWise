import { Link } from 'react-router-dom'
import BrandLogo from '../components/BrandLogo'

interface LandingPageProps {
  isAuthenticated: boolean
}

export default function LandingPage({ isAuthenticated }: LandingPageProps) {
  return (
    <div className="marketing-shell">
      <header className="marketing-nav">
        <Link to="/" className="marketing-brand">
          <BrandLogo alt="Dropwise" className="marketing-brand-logo" />
        </Link>
        <div className="marketing-nav-actions">
          <Link to="/sign-in" className="marketing-link">Sign in</Link>
          <Link to="/sign-up" className="marketing-button marketing-button-primary">Start free</Link>
        </div>
      </header>

      <section className="hero-section">
        <div className="hero-copy reveal-up">
          <p className="hero-eyebrow">Slack + ConnectWise</p>
          <h1>Run ticket operations from Slack without losing ConnectWise accuracy.</h1>
          <p className="hero-subtitle">
            SlackWise syncs ticket updates, notes, and ownership actions so your team can respond faster without context switching.
          </p>
          <div className="hero-actions">
            <Link
              to={isAuthenticated ? '/app/dashboard' : '/sign-up'}
              className="marketing-button marketing-button-primary"
            >
              {isAuthenticated ? 'Open app' : 'Start free'}
            </Link>
            <Link to="/sign-in" className="marketing-button marketing-button-secondary">I already have an account</Link>
          </div>
        </div>
        <div className="hero-card reveal-up delay-1">
          <h2>How it works</h2>
          <ol>
            <li>Connect your Slack workspace and ConnectWise API credentials.</li>
            <li>Choose default channels and routing rules by customer or subject.</li>
            <li>Reply in Slack threads and keep ConnectWise notes/time entries in sync.</li>
          </ol>
        </div>
      </section>

      <section className="feature-grid reveal-up delay-2">
        <article className="feature-card">
          <h3>Thread-native collaboration</h3>
          <p>Every ticket maps to a Slack thread so updates stay grouped, searchable, and actionable.</p>
        </article>
        <article className="feature-card">
          <h3>Routing controls</h3>
          <p>Direct ticket traffic to the right channel and assignee based on your own rule conditions.</p>
        </article>
        <article className="feature-card">
          <h3>Operational visibility</h3>
          <p>Track open tickets, response behavior, and board/status breakdowns from one dashboard.</p>
        </article>
      </section>

      <section className="faq-section reveal-up delay-3">
        <h2>Launch checklist</h2>
        <ul>
          <li>Set Slack app redirect URL and bot scopes in your environment.</li>
          <li>Configure tenant defaults in Settings.</li>
          <li>Create at least one routing rule before onboarding your team.</li>
        </ul>
      </section>
    </div>
  )
}
