import { type FormEvent, useEffect, useMemo, useState } from 'react'
import '../styles/Rules.css'

type FieldOption = 'SUBJECT' | 'CONTACT' | 'COMPANY_ID'
type OperatorOption = 'CONTAINS' | 'EQUALS'
type JoinOption = 'AND' | 'OR'

interface RoutingRule {
  ruleId: string
  tenantId: string
  priority: number
  enabled: boolean
  targetChannelId?: string
  targetAssigneeIdentifier?: string
  primaryField?: FieldOption
  primaryOperator?: OperatorOption
  primaryValue?: string
  secondaryField?: FieldOption
  secondaryOperator?: OperatorOption
  secondaryValue?: string
  joinOperator?: JoinOption
  matchContact?: string
  matchSubject?: string
  matchSubjectRegex?: string
}

interface RuleFormState {
  primaryField: FieldOption
  primaryOperator: OperatorOption
  primaryValue: string
  withSecondary: boolean
  secondaryField: FieldOption
  secondaryOperator: OperatorOption
  secondaryValue: string
  joinOperator: JoinOption
  targetChannelId: string
  targetAssigneeIdentifier: string
}

const fieldLabels: Record<FieldOption, string> = {
  SUBJECT: 'Ticket Subject',
  CONTACT: 'Contact Name',
  COMPANY_ID: 'Company ID',
}

const operatorLabels: Record<OperatorOption, string> = {
  CONTAINS: 'contains',
  EQUALS: 'equals',
}

const initialFormState: RuleFormState = {
  primaryField: 'SUBJECT',
  primaryOperator: 'CONTAINS',
  primaryValue: '',
  withSecondary: false,
  secondaryField: 'CONTACT',
  secondaryOperator: 'EQUALS',
  secondaryValue: '',
  joinOperator: 'AND',
  targetChannelId: '',
  targetAssigneeIdentifier: '',
}

export default function Rules() {
  const [tenantId, setTenantId] = useState('')
  const [rules, setRules] = useState<RoutingRule[]>([])
  const [form, setForm] = useState<RuleFormState>(initialFormState)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const nextPriority = useMemo(() => {
    if (rules.length === 0) return 1
    return Math.max(...rules.map((rule) => rule.priority || 0)) + 1
  }, [rules])

  useEffect(() => {
    const bootstrap = async () => {
      try {
        const response = await fetch('/api/tenants/default')
        if (!response.ok) {
          setError('Could not load default tenant. Enter tenant ID manually.')
          return
        }
        const data = await response.json()
        const defaultTenantId = (data?.tenantId ?? '').trim()
        if (!defaultTenantId) {
          setError('No default tenant configured. Enter tenant ID manually.')
          return
        }
        setTenantId(defaultTenantId)
        await loadRules(defaultTenantId)
      } catch {
        setError('Could not load default tenant. Enter tenant ID manually.')
      }
    }

    bootstrap()
  }, [])

  const loadRules = async (tenant: string) => {
    const trimmedTenant = tenant.trim()
    if (!trimmedTenant) {
      setError('Tenant ID is required.')
      return
    }

    setLoading(true)
    setError(null)
    setActionError(null)
    try {
      const response = await fetch(`/api/tenants/${encodeURIComponent(trimmedTenant)}/rules`)
      if (!response.ok) {
        throw new Error('Failed to load rules from AWS.')
      }
      const data = await response.json()
      const sortedRules = (Array.isArray(data) ? data : []).sort(
        (a: RoutingRule, b: RoutingRule) => (a.priority || 0) - (b.priority || 0),
      )
      setRules(sortedRules)
      setSuccessMessage('Rules loaded from AWS.')
    } catch (err: unknown) {
      setError(readErrorMessage(err, 'Failed to load rules.'))
    } finally {
      setLoading(false)
    }
  }

  const handleCreateRule = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setActionError(null)
    setSuccessMessage(null)

    const primaryValue = form.primaryValue.trim()
    const secondaryValue = form.secondaryValue.trim()
    const targetChannelId = form.targetChannelId.trim()
    const targetAssigneeIdentifier = form.targetAssigneeIdentifier.trim()

    if (!tenantId.trim()) {
      setActionError('Tenant ID is required.')
      return
    }
    if (!primaryValue) {
      setActionError('Primary rule value is required.')
      return
    }
    if (!targetChannelId && !targetAssigneeIdentifier) {
      setActionError('Set a destination channel, assignee, or both.')
      return
    }
    if (form.withSecondary && !secondaryValue) {
      setActionError('Secondary rule value is required when using a second condition.')
      return
    }

    const payload: Partial<RoutingRule> = {
      priority: nextPriority,
      enabled: true,
      primaryField: form.primaryField,
      primaryOperator: form.primaryOperator,
      primaryValue,
      targetChannelId: targetChannelId || undefined,
      targetAssigneeIdentifier: targetAssigneeIdentifier || undefined,
      joinOperator: form.withSecondary ? form.joinOperator : undefined,
      secondaryField: form.withSecondary ? form.secondaryField : undefined,
      secondaryOperator: form.withSecondary ? form.secondaryOperator : undefined,
      secondaryValue: form.withSecondary ? secondaryValue : undefined,
    }

    setSaving(true)
    try {
      const response = await fetch(`/api/tenants/${encodeURIComponent(tenantId.trim())}/rules`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        throw new Error('Failed to create rule.')
      }
      setForm(initialFormState)
      await loadRules(tenantId)
      setSuccessMessage('Rule created and stored in AWS.')
    } catch (err: unknown) {
      setActionError(readErrorMessage(err, 'Failed to create rule.'))
    } finally {
      setSaving(false)
    }
  }

  const handleToggleRule = async (rule: RoutingRule) => {
    setActionError(null)
    setSuccessMessage(null)
    try {
      const response = await fetch(
        `/api/tenants/${encodeURIComponent(tenantId.trim())}/rules/${encodeURIComponent(rule.ruleId)}`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...rule, enabled: !rule.enabled }),
        },
      )
      if (!response.ok) {
        throw new Error('Failed to update rule status.')
      }
      await loadRules(tenantId)
      setSuccessMessage(`Rule ${rule.enabled ? 'disabled' : 'enabled'}.`)
    } catch (err: unknown) {
      setActionError(readErrorMessage(err, 'Failed to update rule.'))
    }
  }

  const handleDeleteRule = async (rule: RoutingRule) => {
    setActionError(null)
    setSuccessMessage(null)
    try {
      const response = await fetch(
        `/api/tenants/${encodeURIComponent(tenantId.trim())}/rules/${encodeURIComponent(rule.ruleId)}?priority=${rule.priority}`,
        { method: 'DELETE' },
      )
      if (!response.ok) {
        throw new Error('Failed to delete rule.')
      }
      await loadRules(tenantId)
      setSuccessMessage('Rule deleted.')
    } catch (err: unknown) {
      setActionError(readErrorMessage(err, 'Failed to delete rule.'))
    }
  }

  const sentencePreview = useMemo(() => {
    const primaryValue = form.primaryValue.trim() || '...'
    const secondaryClause = form.withSecondary
      ? ` ${form.joinOperator} ${fieldLabels[form.secondaryField]} ${operatorLabels[form.secondaryOperator]} "${form.secondaryValue.trim() || '...'}"`
      : ''

    const destination = form.targetChannelId.trim() || '[destination]'
    const assignee = form.targetAssigneeIdentifier.trim() || '[user]'

    return `If ${fieldLabels[form.primaryField]} ${operatorLabels[form.primaryOperator]} "${primaryValue}"${secondaryClause}, send ticket to ${destination} and assign ticket to ${assignee}.`
  }, [form])

  return (
    <div className="rules-container">
      <h1>Rules</h1>
      <p>Create simple routing and assignment rules, stored in AWS DynamoDB.</p>

      <section className="rules-card">
        <p className="tenant-fixed">Tenant: <strong>{tenantId || 'Not configured'}</strong></p>
        {error ? <p className="message error">{error}</p> : null}
      </section>

      <section className="rules-card">
        <h2>Create Rule</h2>
        <form className="rule-form" onSubmit={handleCreateRule}>
          <div className="rule-line">
            <span>If</span>
            <select
              value={form.primaryField}
              onChange={(event) => setForm((current) => ({ ...current, primaryField: event.target.value as FieldOption }))}
            >
              <option value="SUBJECT">Ticket Subject</option>
              <option value="CONTACT">Contact Name</option>
              <option value="COMPANY_ID">Company ID</option>
            </select>
            <select
              value={form.primaryOperator}
              onChange={(event) => setForm((current) => ({ ...current, primaryOperator: event.target.value as OperatorOption }))}
            >
              <option value="CONTAINS">contains</option>
              <option value="EQUALS">equals</option>
            </select>
            <input
              value={form.primaryValue}
              onChange={(event) => setForm((current) => ({ ...current, primaryValue: event.target.value }))}
              placeholder="value"
            />
          </div>

          <div className="toggle-row">
            <label>
              <input
                type="checkbox"
                checked={form.withSecondary}
                onChange={(event) => setForm((current) => ({ ...current, withSecondary: event.target.checked }))}
              />
              Add a second condition
            </label>
          </div>

          {form.withSecondary ? (
            <div className="rule-line">
              <select
                value={form.joinOperator}
                onChange={(event) => setForm((current) => ({ ...current, joinOperator: event.target.value as JoinOption }))}
              >
                <option value="AND">AND</option>
                <option value="OR">OR</option>
              </select>
              <select
                value={form.secondaryField}
                onChange={(event) => setForm((current) => ({ ...current, secondaryField: event.target.value as FieldOption }))}
              >
                <option value="SUBJECT">Ticket Subject</option>
                <option value="CONTACT">Contact Name</option>
                <option value="COMPANY_ID">Company ID</option>
              </select>
              <select
                value={form.secondaryOperator}
                onChange={(event) => setForm((current) => ({ ...current, secondaryOperator: event.target.value as OperatorOption }))}
              >
                <option value="CONTAINS">contains</option>
                <option value="EQUALS">equals</option>
              </select>
              <input
                value={form.secondaryValue}
                onChange={(event) => setForm((current) => ({ ...current, secondaryValue: event.target.value }))}
                placeholder="value"
              />
            </div>
          ) : null}

          <div className="rule-line actions-line">
            <span>send ticket to</span>
            <input
              value={form.targetChannelId}
              onChange={(event) => setForm((current) => ({ ...current, targetChannelId: event.target.value }))}
              placeholder="Slack channel ID"
            />
            <span>and assign to</span>
            <input
              value={form.targetAssigneeIdentifier}
              onChange={(event) => setForm((current) => ({ ...current, targetAssigneeIdentifier: event.target.value }))}
              placeholder="ConnectWise user identifier"
            />
          </div>

          <p className="preview">{sentencePreview}</p>

          <div className="form-actions">
            <button type="submit" disabled={saving || loading}>
              {saving ? 'Saving...' : `Create Rule (priority ${nextPriority})`}
            </button>
          </div>
        </form>
        {actionError ? <p className="message error">{actionError}</p> : null}
        {successMessage ? <p className="message success">{successMessage}</p> : null}
      </section>

      <section className="rules-card">
        <h2>Saved Rules</h2>
        {loading ? <p>Loading rules...</p> : null}
        {!loading && rules.length === 0 ? <p>No rules found for this tenant.</p> : null}
        {!loading && rules.length > 0 ? (
          <div className="rules-list">
            {rules.map((rule) => (
              <article className={`saved-rule ${rule.enabled ? '' : 'disabled'}`} key={rule.ruleId}>
                <p className="rule-summary">
                  <strong>#{rule.priority}</strong> {renderRuleSummary(rule)}
                </p>
                <div className="rule-meta">
                  <span>{rule.enabled ? 'Enabled' : 'Disabled'}</span>
                  <span>Rule ID: {rule.ruleId}</span>
                </div>
                <div className="rule-actions">
                  <button type="button" className="secondary-btn" onClick={() => handleToggleRule(rule)}>
                    {rule.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button type="button" className="danger-btn" onClick={() => handleDeleteRule(rule)}>
                    Delete
                  </button>
                </div>
              </article>
            ))}
          </div>
        ) : null}
      </section>
    </div>
  )
}

function readErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback
}

function renderRuleSummary(rule: RoutingRule): string {
  const sentencePrimary = buildSentenceCondition(rule.primaryField, rule.primaryOperator, rule.primaryValue)
  if (sentencePrimary) {
    const sentenceSecondary = buildSentenceCondition(rule.secondaryField, rule.secondaryOperator, rule.secondaryValue)
    const joiner = rule.joinOperator === 'OR' ? 'OR' : 'AND'
    const combinedCondition = sentenceSecondary ? `${sentencePrimary} ${joiner} ${sentenceSecondary}` : sentencePrimary
    const destination = rule.targetChannelId ? rule.targetChannelId : '[no destination]'
    const assignee = rule.targetAssigneeIdentifier ? rule.targetAssigneeIdentifier : '[no assignee]'
    return `If ${combinedCondition}, send to ${destination} and assign to ${assignee}.`
  }

  const legacyPieces: string[] = []
  if (rule.matchContact) legacyPieces.push(`contact contains "${rule.matchContact}"`)
  if (rule.matchSubject) legacyPieces.push(`subject contains "${rule.matchSubject}"`)
  if (rule.matchSubjectRegex) legacyPieces.push(`subject regex "${rule.matchSubjectRegex}"`)
  const legacyCondition = legacyPieces.length > 0 ? legacyPieces.join(' AND ') : 'legacy rule'
  const destination = rule.targetChannelId ? rule.targetChannelId : '[no destination]'
  return `If ${legacyCondition}, send to ${destination}.`
}

function buildSentenceCondition(field?: FieldOption, operator?: OperatorOption, value?: string): string | null {
  if (!field || !operator || !value) {
    return null
  }
  const prettyField = fieldLabels[field] || field
  const prettyOperator = operatorLabels[operator] || operator.toLowerCase()
  return `${prettyField} ${prettyOperator} "${value}"`
}
