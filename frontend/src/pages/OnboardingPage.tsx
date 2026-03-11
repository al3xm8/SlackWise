import {
  useEffect,
  useMemo,
  useState,
  type ChangeEvent,
  type FormEvent,
} from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BrandLogo from '../components/BrandLogo'
import SlackLogoIcon from '../components/SlackLogoIcon'
import { apiFetch } from '../utils/apiClient'

interface OnboardingPageProps {
  userName: string
  onComplete: () => void
  onSignOut: () => void
}

interface TenantConfigDto {
  tenantId?: string
  slackTeamId?: string
  slackBotToken?: string
  defaultChannelId?: string
  connectwiseSite?: string
  connectwiseClientId?: string
  connectwisePublicKey?: string
  connectwisePrivateKey?: string
  displayName?: string
  autoAssignmentDelayMinutes?: number
  assignmentExclusionKeywords?: string
  trackedCompanyIds?: string
  themeMode?: string
}

interface ConnectWiseFormState {
  connectwiseSite: string
  connectwiseClientId: string
  connectwisePublicKey: string
  connectwisePrivateKey: string
}

const CONNECTWISE_CLIENT_ID_URL = 'https://developer.connectwise.com/ClientID'
const CONNECTWISE_MANAGE_URL = 'https://na.myconnectwise.net/v2025_1/connectwise.aspx?fullscreen=false&locale=en_US#XQAACAC6AAAAAAAAAAA9iIoG07$U9XgivLgsNhRr_aVD2cDkUEZ61FkRVypyQ0Tdh$QkTf7YBX0O1O4KmbnUQ73TJWM6ddqb__d0mWQmBDNrguYuixb97BxCC77xB01FsPa0AhzQHRnGZoXPngWtu7r2zGeRI5wJkXljaKgVLQBCGLhnoMdn1hUUitcluO66_85tEAA='

const emptyConnectWiseForm = (): ConnectWiseFormState => ({
  connectwiseSite: '',
  connectwiseClientId: '',
  connectwisePublicKey: '',
  connectwisePrivateKey: '',
})

const isPresent = (value?: string | null): boolean => (value ?? '').trim().length > 0

const looksLikeSlackChannelId = (value: string): boolean => /^[CG][A-Z0-9]{8,}$/.test(value.trim())

function extractConnectWiseForm(config: TenantConfigDto): ConnectWiseFormState {
  return {
    connectwiseSite: config.connectwiseSite ?? '',
    connectwiseClientId: config.connectwiseClientId ?? '',
    connectwisePublicKey: config.connectwisePublicKey ?? '',
    connectwisePrivateKey: config.connectwisePrivateKey ?? '',
  }
}

function hasConnectWiseConfig(config: TenantConfigDto): boolean {
  return (
    isPresent(config.connectwiseSite) &&
    isPresent(config.connectwiseClientId) &&
    isPresent(config.connectwisePublicKey) &&
    isPresent(config.connectwisePrivateKey)
  )
}

export default function OnboardingPage({ userName, onComplete, onSignOut }: OnboardingPageProps) {
  const navigate = useNavigate()

  const [tenantId, setTenantId] = useState('')
  const [config, setConfig] = useState<TenantConfigDto>({})
  const [defaultChannelId, setDefaultChannelId] = useState('')
  const [botInvited, setBotInvited] = useState(false)
  const [connectWiseForm, setConnectWiseForm] = useState<ConnectWiseFormState>(emptyConnectWiseForm)

  const [loading, setLoading] = useState(true)
  const [checkingSlack, setCheckingSlack] = useState(false)
  const [savingSlack, setSavingSlack] = useState(false)
  const [savingConnectWise, setSavingConnectWise] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const slackConnected = useMemo(() => isPresent(config.slackBotToken), [config.slackBotToken])
  const slackChannelSaved = useMemo(() => isPresent(config.defaultChannelId), [config.defaultChannelId])
  const connectWiseConfigured = useMemo(() => hasConnectWiseConfig(config), [config])
  const slackStageComplete = slackConnected && slackChannelSaved && botInvited
  const onboardingComplete = slackStageComplete && connectWiseConfigured
  const completedSections = (slackStageComplete ? 1 : 0) + (connectWiseConfigured ? 1 : 0)

  const applyConfig = (nextConfig: TenantConfigDto) => {
    setConfig(nextConfig)

    const savedChannel = (nextConfig.defaultChannelId ?? '').trim()
    setDefaultChannelId(savedChannel)
    if (savedChannel.length > 0) {
      setBotInvited(true)
    }

    setConnectWiseForm(extractConnectWiseForm(nextConfig))
  }

  const fetchTenantConfig = async (targetTenantId: string): Promise<TenantConfigDto> => {
    const response = await apiFetch(`/api/tenants/${encodeURIComponent(targetTenantId)}`)
    if (response.status === 404) {
      return {}
    }

    if (!response.ok) {
      throw new Error('Failed to load tenant configuration')
    }

    return await response.json() as TenantConfigDto
  }

  const putTenantConfig = async (patch: Partial<TenantConfigDto>): Promise<TenantConfigDto> => {
    if (!tenantId) {
      throw new Error('Tenant ID is not available yet')
    }

    const latestConfig = await fetchTenantConfig(tenantId)
    const mergedPayload: TenantConfigDto = {
      ...latestConfig,
      ...patch,
      tenantId,
    }

    const response = await apiFetch(`/api/tenants/${encodeURIComponent(tenantId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(mergedPayload),
    })

    if (!response.ok) {
      throw new Error('Failed to save tenant configuration')
    }

    return await fetchTenantConfig(tenantId)
  }

  useEffect(() => {
    const bootstrap = async () => {
      setLoading(true)
      setErrorMessage(null)

      try {
        const defaultResponse = await apiFetch('/api/tenants/default')
        if (!defaultResponse.ok) {
          throw new Error('Could not load default tenant')
        }

        const defaultData = await defaultResponse.json() as { tenantId?: string }
        const resolvedTenantId = (defaultData.tenantId ?? '').trim()
        if (!resolvedTenantId) {
          throw new Error('No default tenant configured')
        }

        setTenantId(resolvedTenantId)
        const initialConfig = await fetchTenantConfig(resolvedTenantId)
        applyConfig(initialConfig)
      } catch (error: unknown) {
        if (error instanceof Error) {
          setErrorMessage(error.message)
        } else {
          setErrorMessage('Failed to initialize onboarding')
        }
      } finally {
        setLoading(false)
      }
    }

    bootstrap()
  }, [])

  const handleStartSlackInstall = () => {
    if (!tenantId) {
      setErrorMessage('Tenant ID is required before connecting Slack')
      return
    }

    const installUrl = `/api/slack/oauth/install?tenantId=${encodeURIComponent(tenantId)}`
    window.location.href = installUrl
  }

  const handleCheckSlackConnection = async () => {
    if (!tenantId) {
      setErrorMessage('Tenant ID is required before checking Slack connection')
      return
    }

    setCheckingSlack(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const nextConfig = await fetchTenantConfig(tenantId)
      applyConfig(nextConfig)
      if (isPresent(nextConfig.slackBotToken)) {
        setSuccessMessage('Slack connection confirmed.')
      } else {
        setSuccessMessage('Slack not connected yet. Finish Slack install, then recheck.')
      }
    } catch {
      setErrorMessage('Could not verify Slack connection right now.')
    } finally {
      setCheckingSlack(false)
    }
  }

  const handleSaveSlackChannel = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)
    setSuccessMessage(null)

    const channel = defaultChannelId.trim().toUpperCase()

    if (!slackConnected) {
      setErrorMessage('Connect Slack first, then set the default Slack channel.')
      return
    }

    if (!botInvited) {
      setErrorMessage('Confirm you invited @DropwiseBot to the channel before continuing.')
      return
    }

    if (!looksLikeSlackChannelId(channel)) {
      setErrorMessage('Enter a valid Slack channel ID (for example C012ABCDEF1 or G012ABCDEF1).')
      return
    }

    setSavingSlack(true)
    try {
      const nextConfig = await putTenantConfig({ defaultChannelId: channel })
      applyConfig(nextConfig)
      setSuccessMessage('Slack channel saved. ConnectWise setup is now unlocked.')
    } catch {
      setErrorMessage('Failed to save the default Slack channel.')
    } finally {
      setSavingSlack(false)
    }
  }

  const handleConnectWiseInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target
    setConnectWiseForm((current) => ({
      ...current,
      [name]: value,
    }))
  }

  const handleSaveConnectWise = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)
    setSuccessMessage(null)

    if (!slackStageComplete) {
      setErrorMessage('Complete the Slack section before ConnectWise setup.')
      return
    }

    const payload: ConnectWiseFormState = {
      connectwiseSite: connectWiseForm.connectwiseSite.trim(),
      connectwiseClientId: connectWiseForm.connectwiseClientId.trim(),
      connectwisePublicKey: connectWiseForm.connectwisePublicKey.trim(),
      connectwisePrivateKey: connectWiseForm.connectwisePrivateKey.trim(),
    }

    if (!hasConnectWiseConfig(payload)) {
      setErrorMessage('All ConnectWise fields are required before you continue.')
      return
    }

    setSavingConnectWise(true)

    try {
      const nextConfig = await putTenantConfig(payload)
      applyConfig(nextConfig)
      setSuccessMessage('ConnectWise credentials saved. You can now finish onboarding.')
    } catch {
      setErrorMessage('Failed to save ConnectWise credentials.')
    } finally {
      setSavingConnectWise(false)
    }
  }

  const handleFinishOnboarding = () => {
    if (!onboardingComplete) {
      setErrorMessage('Complete Slack and ConnectWise setup before finishing onboarding.')
      return
    }

    onComplete()
    navigate('/app/dashboard', { replace: true })
  }

  return (
    <div className="onboarding-shell">
      <div className="onboarding-card reveal-up">
        <Link to="/" className="setup-brand" aria-label="Back to landing page">
          <BrandLogo alt="Dropwise" className="setup-brand-logo" />
        </Link>
        <p className="onboarding-eyebrow">Onboarding</p>
        <h1>Welcome {userName || 'there'}, let&apos;s finish setup.</h1>
        <p className="onboarding-subtitle">
          Complete Slack first. ConnectWise unlocks only after Slack is connected, bot invite is confirmed,
          and a default channel is saved.
        </p>

        <div className="onboarding-progress" role="status" aria-live="polite">
          {completedSections} of 2 sections completed
        </div>

        {errorMessage ? <p className="onboarding-alert onboarding-alert-error">{errorMessage}</p> : null}
        {successMessage ? <p className="onboarding-alert onboarding-alert-success">{successMessage}</p> : null}

        <section className="onboarding-stage">
          <div className="onboarding-stage-header">
            <h2>1. Slack Workspace</h2>
            <span className={`onboarding-stage-status${slackStageComplete ? ' complete' : ''}`}>
              {slackStageComplete ? 'Complete' : 'Required'}
            </span>
          </div>

          <ol className="onboarding-stage-list">
            <li>Click <strong>Add to Slack Workspace</strong> and approve installation.</li>
            <li>
              In Slack, run <code className="onboarding-inline-code">/invite @DropwiseBot</code>
              {' '}in the channel you want as default.
            </li>
            <li>Paste that channel ID below and save it.</li>
          </ol>

          <div className="onboarding-slack-actions">
            <button
              type="button"
              className="marketing-button marketing-button-primary onboarding-slack-button"
              onClick={handleStartSlackInstall}
              disabled={loading || !tenantId}
            >
              <SlackLogoIcon className="slack-logo-icon" />
              Add to Slack Workspace
            </button>

            <button
              type="button"
              className="marketing-button marketing-button-secondary"
              onClick={handleCheckSlackConnection}
              disabled={loading || checkingSlack || !tenantId}
            >
              {checkingSlack ? 'Checking Slack...' : 'Confirm Slack Connection'}
            </button>
          </div>

          <form className="onboarding-stage-form" onSubmit={handleSaveSlackChannel}>
            <label htmlFor="defaultChannelId">Default Slack Channel ID</label>
            <input
              id="defaultChannelId"
              name="defaultChannelId"
              className="onboarding-input"
              value={defaultChannelId}
              onChange={(event) => setDefaultChannelId(event.target.value)}
              placeholder="C012ABCDEF1"
              disabled={loading || savingSlack}
              autoComplete="off"
            />

            <label className="onboarding-checkbox" htmlFor="botInvited">
              <input
                id="botInvited"
                type="checkbox"
                checked={botInvited}
                onChange={(event) => setBotInvited(event.target.checked)}
                disabled={loading || savingSlack}
              />
              <span>I invited the bot with <code className="onboarding-inline-code">/invite @DropwiseBot</code>.</span>
            </label>

            <button
              type="submit"
              className="marketing-button marketing-button-secondary"
              disabled={loading || savingSlack || !tenantId}
            >
              {savingSlack ? 'Saving channel...' : 'Save Slack Channel'}
            </button>
          </form>
        </section>

        <section className={`onboarding-stage${slackStageComplete ? '' : ' disabled'}`}>
          <div className="onboarding-stage-header">
            <h2>2. ConnectWise API</h2>
            <span className={`onboarding-stage-status${connectWiseConfigured ? ' complete' : ''}`}>
              {connectWiseConfigured ? 'Complete' : (slackStageComplete ? 'Required' : 'Locked')}
            </span>
          </div>

          <p className="onboarding-stage-note">
            Use these pages to create your Client ID and API keys.
          </p>

          <div className="onboarding-link-list">
            <a href={CONNECTWISE_CLIENT_ID_URL} target="_blank" rel="noreferrer">
              Open ConnectWise Client ID Registration
            </a>
            <a href={CONNECTWISE_MANAGE_URL} target="_blank" rel="noreferrer">
              Open ConnectWise PSA / Manage
            </a>
          </div>

          <ol className="onboarding-stage-list">
            <li>Create or confirm your Client ID at the ConnectWise developer page.</li>
            <li>In ConnectWise PSA, go to <strong>System &gt; Members &gt; API Members</strong>.</li>
            <li>Create an API Member and assign a security role with required API permissions.</li>
            <li>On that API Member, open the <strong>API Keys</strong> tab and create a new key pair.</li>
            <li>Copy the Public Key and Private Key immediately, then paste all fields below.</li>
          </ol>

          <form className="onboarding-stage-form" onSubmit={handleSaveConnectWise}>
            <fieldset className="onboarding-fieldset" disabled={!slackStageComplete || savingConnectWise || loading}>
              <div className="onboarding-field-grid">
                <label className="onboarding-field" htmlFor="connectwiseSite">
                  <span>ConnectWise Site</span>
                  <input
                    id="connectwiseSite"
                    name="connectwiseSite"
                    className="onboarding-input"
                    value={connectWiseForm.connectwiseSite}
                    onChange={handleConnectWiseInputChange}
                    placeholder="na.myconnectwise.net"
                    autoComplete="off"
                  />
                </label>

                <label className="onboarding-field" htmlFor="connectwiseClientId">
                  <span>ConnectWise Client ID</span>
                  <input
                    id="connectwiseClientId"
                    name="connectwiseClientId"
                    className="onboarding-input"
                    value={connectWiseForm.connectwiseClientId}
                    onChange={handleConnectWiseInputChange}
                    placeholder="Your registered client ID"
                    autoComplete="off"
                  />
                </label>

                <label className="onboarding-field" htmlFor="connectwisePublicKey">
                  <span>ConnectWise Public Key</span>
                  <input
                    id="connectwisePublicKey"
                    name="connectwisePublicKey"
                    className="onboarding-input"
                    value={connectWiseForm.connectwisePublicKey}
                    onChange={handleConnectWiseInputChange}
                    placeholder="Public key"
                    autoComplete="off"
                  />
                </label>

                <label className="onboarding-field" htmlFor="connectwisePrivateKey">
                  <span>ConnectWise Private Key</span>
                  <input
                    id="connectwisePrivateKey"
                    name="connectwisePrivateKey"
                    className="onboarding-input"
                    value={connectWiseForm.connectwisePrivateKey}
                    onChange={handleConnectWiseInputChange}
                    placeholder="Private key"
                    autoComplete="off"
                  />
                </label>
              </div>

              <button type="submit" className="marketing-button marketing-button-secondary">
                {savingConnectWise ? 'Saving ConnectWise...' : 'Save ConnectWise Credentials'}
              </button>
            </fieldset>
          </form>
        </section>

        <div className="onboarding-actions">
          <button type="button" className="marketing-link marketing-link-button" onClick={onSignOut}>
            Use a different account
          </button>
          <button
            type="button"
            className="marketing-button marketing-button-primary"
            onClick={handleFinishOnboarding}
            disabled={!onboardingComplete || loading}
          >
            Finish onboarding
          </button>
        </div>
      </div>
    </div>
  )
}




