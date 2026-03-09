import { type ChangeEvent, type FormEvent, useEffect, useMemo, useState } from 'react'
import '../styles/Settings.css'
import { applyThemeMode, normalizeThemeMode, storeThemeMode, type ThemeMode } from '../utils/theme'

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

interface SettingsFormState {
  displayName: string
  slackTeamId: string
  slackBotToken: string
  defaultChannelId: string
  connectwiseSite: string
  connectwiseClientId: string
  connectwisePublicKey: string
  connectwisePrivateKey: string
  autoAssignmentDelayMinutes: string
  assignmentExclusionKeywords: string
  trackedCompanyIds: string
  themeMode: ThemeMode
}

const makeEmptyFormState = (): SettingsFormState => ({
  displayName: '',
  slackTeamId: '',
  slackBotToken: '',
  defaultChannelId: '',
  connectwiseSite: '',
  connectwiseClientId: '',
  connectwisePublicKey: '',
  connectwisePrivateKey: '',
  autoAssignmentDelayMinutes: '',
  assignmentExclusionKeywords: '',
  trackedCompanyIds: '',
  themeMode: typeof document === 'undefined'
    ? 'light'
    : normalizeThemeMode(document.documentElement.getAttribute('data-theme')),
})

const toFormState = (config?: TenantConfigDto | null): SettingsFormState => ({
  displayName: config?.displayName ?? '',
  slackTeamId: config?.slackTeamId ?? '',
  slackBotToken: config?.slackBotToken ?? '',
  defaultChannelId: config?.defaultChannelId ?? '',
  connectwiseSite: config?.connectwiseSite ?? '',
  connectwiseClientId: config?.connectwiseClientId ?? '',
  connectwisePublicKey: config?.connectwisePublicKey ?? '',
  connectwisePrivateKey: config?.connectwisePrivateKey ?? '',
  autoAssignmentDelayMinutes:
    config?.autoAssignmentDelayMinutes !== undefined && config?.autoAssignmentDelayMinutes !== null
      ? String(config.autoAssignmentDelayMinutes)
      : '',
  assignmentExclusionKeywords: config?.assignmentExclusionKeywords ?? '',
  trackedCompanyIds: config?.trackedCompanyIds ?? '',
  themeMode: normalizeThemeMode(config?.themeMode),
})

const toPayload = (form: SettingsFormState): TenantConfigDto => {
  const normalize = (value: string): string | undefined => {
    const trimmed = value.trim()
    return trimmed.length > 0 ? trimmed : undefined
  }

  const delayValue = form.autoAssignmentDelayMinutes.trim()
  const parsedDelay = delayValue.length > 0 ? Number.parseInt(delayValue, 10) : Number.NaN

  return {
    displayName: normalize(form.displayName),
    slackTeamId: normalize(form.slackTeamId),
    slackBotToken: normalize(form.slackBotToken),
    defaultChannelId: normalize(form.defaultChannelId),
    connectwiseSite: normalize(form.connectwiseSite),
    connectwiseClientId: normalize(form.connectwiseClientId),
    connectwisePublicKey: normalize(form.connectwisePublicKey),
    connectwisePrivateKey: normalize(form.connectwisePrivateKey),
    autoAssignmentDelayMinutes:
      Number.isNaN(parsedDelay) || parsedDelay < 0
        ? undefined
        : parsedDelay,
    assignmentExclusionKeywords: normalize(form.assignmentExclusionKeywords),
    trackedCompanyIds: normalize(form.trackedCompanyIds),
    themeMode: form.themeMode,
  }
}

export default function Settings() {
  const [tenantId, setTenantId] = useState('')
  const [form, setForm] = useState<SettingsFormState>(makeEmptyFormState)
  const [savedForm, setSavedForm] = useState<SettingsFormState>(makeEmptyFormState)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [hasExistingConfig, setHasExistingConfig] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [showSlackBotToken, setShowSlackBotToken] = useState(false)
  const [showConnectwisePrivateKey, setShowConnectwisePrivateKey] = useState(false)

  useEffect(() => {
    const bootstrap = async () => {
      try {
        const response = await fetch('/api/tenants/default')
        if (!response.ok) {
          throw new Error('Could not load default tenant.')
        }

        const data = await response.json() as { tenantId?: string }
        const defaultTenantId = (data.tenantId ?? '').trim()

        if (defaultTenantId.length === 0) {
          throw new Error('No default tenant configured.')
        }

        setTenantId(defaultTenantId)
        await loadTenantConfig(defaultTenantId)
      } catch (error: unknown) {
        if (error instanceof Error) {
          setErrorMessage(error.message)
        } else {
          setErrorMessage('Could not initialize settings page.')
        }
        setLoading(false)
      }
    }

    bootstrap()
  }, [])

  const loadTenantConfig = async (targetTenantId: string, showReloadMessage = false) => {
    setLoading(true)
    setErrorMessage(null)

    try {
      const response = await fetch(`/api/tenants/${encodeURIComponent(targetTenantId)}`)

      if (response.status === 404) {
        const emptyState = makeEmptyFormState()
        setForm(emptyState)
        setSavedForm(emptyState)
        setHasExistingConfig(false)
        if (showReloadMessage) {
          setSuccessMessage('No saved settings found for this tenant yet.')
        }
        return
      }

      if (!response.ok) {
        throw new Error('Failed to load tenant settings from AWS.')
      }

      const data = await response.json() as TenantConfigDto
      const loadedForm = toFormState(data)
      setForm(loadedForm)
      setSavedForm(loadedForm)
      setHasExistingConfig(true)
      applyThemeMode(loadedForm.themeMode)
      storeThemeMode(loadedForm.themeMode)

      if (showReloadMessage) {
        setSuccessMessage('Settings refreshed from AWS.')
      }
    } catch (error: unknown) {
      if (error instanceof Error) {
        setErrorMessage(error.message)
      } else {
        setErrorMessage('Failed to load tenant settings.')
      }
    } finally {
      setLoading(false)
    }
  }

  const isDirty = useMemo(() => {
    return Object.keys(form).some((key) => {
      const typedKey = key as keyof SettingsFormState
      return form[typedKey] !== savedForm[typedKey]
    })
  }, [form, savedForm])

  const handleFieldChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = event.target
    setForm((current) => ({
      ...current,
      [name]: value,
    }))

    if (name === 'themeMode') {
      const nextTheme = normalizeThemeMode(value)
      applyThemeMode(nextTheme)
      storeThemeMode(nextTheme)
    }
  }

  const handleReset = () => {
    setForm(savedForm)
    applyThemeMode(savedForm.themeMode)
    storeThemeMode(savedForm.themeMode)
    setErrorMessage(null)
    setSuccessMessage('Unsaved changes were discarded.')
  }

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!tenantId) {
      setErrorMessage('Tenant ID is required before saving.')
      return
    }

    const delayValue = form.autoAssignmentDelayMinutes.trim()
    if (delayValue.length > 0) {
      const parsed = Number.parseInt(delayValue, 10)
      if (Number.isNaN(parsed) || parsed < 0) {
        setErrorMessage('Auto-assignment delay must be a whole number 0 or greater.')
        return
      }
    }

    setSaving(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      const response = await fetch(`/api/tenants/${encodeURIComponent(tenantId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(toPayload(form)),
      })

      if (!response.ok) {
        throw new Error('Failed to save tenant settings.')
      }

      setSavedForm(form)
      setHasExistingConfig(true)
      applyThemeMode(form.themeMode)
      storeThemeMode(form.themeMode)
      setSuccessMessage('Settings saved to AWS.')
    } catch (error: unknown) {
      if (error instanceof Error) {
        setErrorMessage(error.message)
      } else {
        setErrorMessage('Failed to save tenant settings.')
      }
    } finally {
      setSaving(false)
    }
  }

  const handleConnectSlackWorkspace = () => {
    if (!tenantId || tenantId.trim().length === 0) {
      setErrorMessage('Tenant ID is required before connecting Slack.')
      return
    }

    const installUrl = `/api/slack/oauth/install?tenantId=${encodeURIComponent(tenantId.trim())}`
    window.location.href = installUrl
  }

  return (
    <div className="settings-container">
      <h1>Settings</h1>
      <p>Manage tenant-level Slack and ConnectWise configuration stored in AWS DynamoDB.</p>

      <section className="settings-card">
        <div className="tenant-header">
          <div>
            <p className="tenant-label">
              Tenant: <strong>{tenantId || 'Not configured'}</strong>
            </p>
            <p className="tenant-state">
              {hasExistingConfig
                ? 'Existing tenant settings loaded.'
                : 'No settings found yet. Saving will create a new config item.'}
            </p>
          </div>
          <button
            type="button"
            className="settings-btn settings-btn-secondary"
            onClick={() => tenantId && loadTenantConfig(tenantId, true)}
            disabled={loading || saving || tenantId.length === 0}
          >
            {loading ? 'Loading...' : 'Reload'}
          </button>
        </div>
      </section>

      {errorMessage ? <p className="settings-alert settings-alert-error">{errorMessage}</p> : null}
      {successMessage ? <p className="settings-alert settings-alert-success">{successMessage}</p> : null}

      <form className="settings-card settings-form" onSubmit={handleSave}>
        <section className="settings-section">
          <h2>Workspace</h2>
          <div className="settings-grid">
            <label className="field-group" htmlFor="displayName">
              <span>Display Name</span>
              <input
                id="displayName"
                name="displayName"
                value={form.displayName}
                onChange={handleFieldChange}
                placeholder="Acme Managed Services"
                disabled={loading || saving}
              />
            </label>

            <label className="field-group" htmlFor="themeMode">
              <span>Theme</span>
              <select
                id="themeMode"
                name="themeMode"
                value={form.themeMode}
                onChange={handleFieldChange}
                disabled={loading || saving}
              >
                <option value="light">Light</option>
                <option value="dark">Dark</option>
              </select>
            </label>
          </div>
        </section>

        <section className="settings-section">
          <h2>Slack</h2>
          <p className="settings-hint">
            Reinstall the Slack app for this workspace to rotate or refresh the bot token.
          </p>
          <div className="settings-inline-actions">
            <button
              type="button"
              className="settings-btn settings-btn-primary"
              onClick={handleConnectSlackWorkspace}
              disabled={loading || saving || tenantId.length === 0}
            >
              Connect Slack Workspace
            </button>
          </div>
          <div className="settings-grid">
            <label className="field-group" htmlFor="slackTeamId">
              <span>Slack Team ID</span>
              <input
                id="slackTeamId"
                name="slackTeamId"
                value={form.slackTeamId}
                onChange={handleFieldChange}
                placeholder="T01234567"
                disabled={loading || saving}
              />
            </label>

            <label className="field-group" htmlFor="defaultChannelId">
              <span>Default Channel ID</span>
              <input
                id="defaultChannelId"
                name="defaultChannelId"
                value={form.defaultChannelId}
                onChange={handleFieldChange}
                placeholder="C01234567"
                disabled={loading || saving}
              />
            </label>

            <label className="field-group field-group-full" htmlFor="slackBotToken">
              <span>Slack Bot Token</span>
              <div className="secret-row">
                <input
                  id="slackBotToken"
                  name="slackBotToken"
                  type={showSlackBotToken ? 'text' : 'password'}
                  value={form.slackBotToken}
                  onChange={handleFieldChange}
                  placeholder="xoxb-..."
                  disabled={loading || saving}
                  autoComplete="off"
                />
                <button
                  type="button"
                  className="settings-btn settings-btn-secondary"
                  onClick={() => setShowSlackBotToken((current) => !current)}
                  disabled={loading || saving}
                >
                  {showSlackBotToken ? 'Hide' : 'Show'}
                </button>
              </div>
            </label>
          </div>
        </section>

        <section className="settings-section">
          <h2>ConnectWise</h2>
          <div className="settings-grid">
            <label className="field-group" htmlFor="connectwiseSite">
              <span>ConnectWise Site</span>
              <input
                id="connectwiseSite"
                name="connectwiseSite"
                value={form.connectwiseSite}
                onChange={handleFieldChange}
                placeholder="na.myconnectwise.net"
                disabled={loading || saving}
              />
            </label>

            <label className="field-group" htmlFor="connectwiseClientId">
              <span>ConnectWise Client ID</span>
              <input
                id="connectwiseClientId"
                name="connectwiseClientId"
                value={form.connectwiseClientId}
                onChange={handleFieldChange}
                placeholder="client-id"
                disabled={loading || saving}
              />
            </label>

            <label className="field-group" htmlFor="connectwisePublicKey">
              <span>ConnectWise Public Key</span>
              <input
                id="connectwisePublicKey"
                name="connectwisePublicKey"
                value={form.connectwisePublicKey}
                onChange={handleFieldChange}
                placeholder="public-key"
                disabled={loading || saving}
                autoComplete="off"
              />
            </label>

            <label className="field-group" htmlFor="connectwisePrivateKey">
              <span>ConnectWise Private Key</span>
              <div className="secret-row">
                <input
                  id="connectwisePrivateKey"
                  name="connectwisePrivateKey"
                  type={showConnectwisePrivateKey ? 'text' : 'password'}
                  value={form.connectwisePrivateKey}
                  onChange={handleFieldChange}
                  placeholder="private-key"
                  disabled={loading || saving}
                  autoComplete="off"
                />
                <button
                  type="button"
                  className="settings-btn settings-btn-secondary"
                  onClick={() => setShowConnectwisePrivateKey((current) => !current)}
                  disabled={loading || saving}
                >
                  {showConnectwisePrivateKey ? 'Hide' : 'Show'}
                </button>
              </div>
            </label>
          </div>
        </section>

        <section className="settings-section">
          <h2>Automation</h2>
          <div className="settings-grid">
            <label className="field-group" htmlFor="autoAssignmentDelayMinutes">
              <span>Auto-assignment delay (minutes)</span>
              <input
                id="autoAssignmentDelayMinutes"
                name="autoAssignmentDelayMinutes"
                type="number"
                min={0}
                step={1}
                value={form.autoAssignmentDelayMinutes}
                onChange={handleFieldChange}
                placeholder="2"
                disabled={loading || saving}
              />
            </label>

            <label className="field-group field-group-full" htmlFor="trackedCompanyIds">
              <span>Tracked company IDs</span>
              <textarea
                id="trackedCompanyIds"
                name="trackedCompanyIds"
                value={form.trackedCompanyIds}
                onChange={handleFieldChange}
                placeholder="19300, 250"
                rows={3}
                disabled={loading || saving}
              />
              <small>Comma, semicolon, or newline separated values.</small>
            </label>

            <label className="field-group field-group-full" htmlFor="assignmentExclusionKeywords">
              <span>Assignment exclusion keywords</span>
              <textarea
                id="assignmentExclusionKeywords"
                name="assignmentExclusionKeywords"
                value={form.assignmentExclusionKeywords}
                onChange={handleFieldChange}
                placeholder="compliance: set and review&#10;internal system vulnerability"
                rows={4}
                disabled={loading || saving}
              />
              <small>When ticket summary contains one of these values, auto-assignment is skipped.</small>
            </label>
          </div>
        </section>

        <div className="settings-actions">
          <button
            type="button"
            className="settings-btn settings-btn-secondary"
            onClick={handleReset}
            disabled={!isDirty || saving || loading}
          >
            Reset
          </button>
          <button type="submit" className="settings-btn settings-btn-primary" disabled={!isDirty || saving || loading}>
            {saving ? 'Saving...' : 'Save Settings'}
          </button>
        </div>
      </form>
    </div>
  )
}
