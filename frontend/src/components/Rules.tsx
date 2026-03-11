import { type FormEvent, useEffect, useMemo, useState } from 'react'
import '../styles/Rules.css'
import { apiFetch } from '../utils/apiClient'

type FieldOption = 'SUBJECT' | 'CONTACT' | 'COMPANY_ID'
type OperatorOption = 'CONTAINS' | 'EQUALS' | 'NOT_EQUALS'
type JoinOption = 'AND' | 'OR'

interface RuleCondition {
  field: FieldOption
  operator: OperatorOption
  value: string
}

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
  conditions?: RuleCondition[]
  matchContact?: string
  matchSubject?: string
  matchSubjectRegex?: string
}

interface RuleFormState {
  conditions: RuleCondition[]
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
  NOT_EQUALS: 'does not equal',
}

const createDefaultCondition = (): RuleCondition => ({
  field: 'SUBJECT',
  operator: 'CONTAINS',
  value: '',
})

const initialFormState: RuleFormState = {
  conditions: [createDefaultCondition()],
  joinOperator: 'AND',
  targetChannelId: '',
  targetAssigneeIdentifier: '',
}

export default function Rules() {
  const [tenantId, setTenantId] = useState('')
  const [rules, setRules] = useState<RoutingRule[]>([])
  const [form, setForm] = useState<RuleFormState>(initialFormState)
  const [editingRule, setEditingRule] = useState<RoutingRule | null>(null)
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
        const response = await apiFetch('/api/tenants/default')
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
      const response = await apiFetch(`/api/tenants/${encodeURIComponent(trimmedTenant)}/rules`)
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

  const handleSaveRule = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setActionError(null)
    setSuccessMessage(null)

    const normalizedConditions = form.conditions
      .map((condition) => ({
        ...condition,
        value: condition.value.trim(),
      }))
      .filter((condition) => condition.value.length > 0)

    const targetChannelId = form.targetChannelId.trim()
    const targetAssigneeIdentifier = form.targetAssigneeIdentifier.trim()

    if (!tenantId.trim()) {
      setActionError('Tenant ID is required.')
      return
    }
    if (normalizedConditions.length === 0) {
      setActionError('At least one condition is required.')
      return
    }
    if (normalizedConditions.length !== form.conditions.length) {
      setActionError('Every condition requires a value.')
      return
    }
    if (!targetChannelId && !targetAssigneeIdentifier) {
      setActionError('Set a destination channel, assignee, or both.')
      return
    }

    const firstCondition = normalizedConditions[0]
    const secondCondition = normalizedConditions[1]

    const payload: Partial<RoutingRule> = {
      priority: editingRule ? editingRule.priority : nextPriority,
      enabled: editingRule ? editingRule.enabled : true,
      conditions: normalizedConditions,
      primaryField: firstCondition?.field,
      primaryOperator: firstCondition?.operator,
      primaryValue: firstCondition?.value,
      targetChannelId: targetChannelId || undefined,
      targetAssigneeIdentifier: targetAssigneeIdentifier || undefined,
      joinOperator: normalizedConditions.length > 1 ? form.joinOperator : undefined,
      secondaryField: secondCondition?.field,
      secondaryOperator: secondCondition?.operator,
      secondaryValue: secondCondition?.value,
    }

    setSaving(true)
    try {
      const isEditing = editingRule != null
      const endpoint = isEditing
        ? `/api/tenants/${encodeURIComponent(tenantId.trim())}/rules/${encodeURIComponent(editingRule.ruleId)}`
        : `/api/tenants/${encodeURIComponent(tenantId.trim())}/rules`

      const response = await apiFetch(endpoint, {
        method: isEditing ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!response.ok) {
        throw new Error(isEditing ? 'Failed to update rule.' : 'Failed to create rule.')
      }
      setForm(initialFormState)
      setEditingRule(null)
      await loadRules(tenantId)
      setSuccessMessage(isEditing ? 'Rule updated.' : 'Rule created and stored in AWS.')
    } catch (err: unknown) {
      setActionError(readErrorMessage(err, editingRule ? 'Failed to update rule.' : 'Failed to create rule.'))
    } finally {
      setSaving(false)
    }
  }

  const handleEditRule = (rule: RoutingRule) => {
    const conditions = getRuleConditions(rule)
    setForm({
      conditions: conditions.length > 0 ? conditions.map((condition) => ({ ...condition })) : [createDefaultCondition()],
      joinOperator: rule.joinOperator === 'OR' ? 'OR' : 'AND',
      targetChannelId: rule.targetChannelId ?? '',
      targetAssigneeIdentifier: rule.targetAssigneeIdentifier ?? '',
    })
    setEditingRule(rule)
    setActionError(null)
    setSuccessMessage(null)
  }

  const handleCancelEdit = () => {
    setEditingRule(null)
    setForm(initialFormState)
    setActionError(null)
  }

  const handleConditionChange = (
    index: number,
    key: keyof RuleCondition,
    value: string,
  ) => {
    setForm((current) => {
      const updated = current.conditions.map((condition, conditionIndex) =>
        conditionIndex === index ? { ...condition, [key]: value } : condition,
      )
      return {
        ...current,
        conditions: updated,
      }
    })
  }

  const handleAddCondition = () => {
    setForm((current) => ({
      ...current,
      conditions: [...current.conditions, createDefaultCondition()],
    }))
  }

  const handleRemoveCondition = (index: number) => {
    setForm((current) => {
      if (current.conditions.length <= 1) {
        return current
      }
      return {
        ...current,
        conditions: current.conditions.filter((_, conditionIndex) => conditionIndex !== index),
      }
    })
  }

  const handleToggleRule = async (rule: RoutingRule) => {
    setActionError(null)
    setSuccessMessage(null)
    try {
      const response = await apiFetch(
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
      const response = await apiFetch(
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
    const conditionPreview = form.conditions
      .map((condition) =>
        `${fieldLabels[condition.field]} ${operatorLabels[condition.operator]} "${condition.value.trim() || '...'}"`,
      )
      .join(` ${form.joinOperator} `)

    const destination = form.targetChannelId.trim() || '[destination]'
    const assignee = form.targetAssigneeIdentifier.trim() || '[user]'

    return `If ${conditionPreview}, send ticket to ${destination} and assign ticket to ${assignee}.`
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
        <h2>{editingRule ? 'Edit Rule' : 'Create Rule'}</h2>
        <form className="rule-form" onSubmit={handleSaveRule}>
          {form.conditions.map((condition, index) => (
            <div className="rule-line" key={index}>
              <span>{index === 0 ? 'If' : form.joinOperator}</span>
              <select
                value={condition.field}
                onChange={(event) => handleConditionChange(index, 'field', event.target.value)}
              >
                <option value="SUBJECT">Ticket Subject</option>
                <option value="CONTACT">Contact Name</option>
                <option value="COMPANY_ID">Company ID</option>
              </select>
              <select
                value={condition.operator}
                onChange={(event) => handleConditionChange(index, 'operator', event.target.value)}
              >
                <option value="CONTAINS">contains</option>
                <option value="EQUALS">equals</option>
                <option value="NOT_EQUALS">does not equal</option>
              </select>
              <input
                value={condition.value}
                onChange={(event) => handleConditionChange(index, 'value', event.target.value)}
                placeholder="value"
              />
              {form.conditions.length > 1 ? (
                <button type="button" className="danger-btn" onClick={() => handleRemoveCondition(index)}>
                  Remove
                </button>
              ) : null}
            </div>
          ))}

          <div className="rule-line">
            <button type="button" className="secondary-btn" onClick={handleAddCondition}>
              Add condition
            </button>
            {form.conditions.length > 1 ? (
              <>
                <span>Use</span>
                <select
                  value={form.joinOperator}
                  onChange={(event) => setForm((current) => ({ ...current, joinOperator: event.target.value as JoinOption }))}
                >
                  <option value="AND">AND</option>
                  <option value="OR">OR</option>
                </select>
                <span>between all conditions</span>
              </>
            ) : null}
          </div>

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
              {saving
                ? 'Saving...'
                : editingRule
                  ? `Save Rule #${editingRule.priority}`
                  : `Create Rule (priority ${nextPriority})`}
            </button>
            {editingRule ? (
              <button type="button" className="secondary-btn" onClick={handleCancelEdit} disabled={saving}>
                Cancel Edit
              </button>
            ) : null}
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
                  <button type="button" className="secondary-btn" onClick={() => handleEditRule(rule)}>
                    Edit
                  </button>
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
  const sentenceConditions = getRuleConditions(rule)
    .map((condition) => buildSentenceCondition(condition.field, condition.operator, condition.value))
    .filter((condition): condition is string => Boolean(condition))

  if (sentenceConditions.length > 0) {
    const joiner = rule.joinOperator === 'OR' ? 'OR' : 'AND'
    const combinedCondition = sentenceConditions.join(` ${joiner} `)
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

function getRuleConditions(rule: RoutingRule): RuleCondition[] {
  if (Array.isArray(rule.conditions) && rule.conditions.length > 0) {
    return rule.conditions.filter((condition) =>
      Boolean(condition?.field && condition?.operator && condition?.value),
    )
  }

  const fallback: RuleCondition[] = []
  if (rule.primaryField && rule.primaryOperator && rule.primaryValue) {
    fallback.push({
      field: rule.primaryField,
      operator: rule.primaryOperator,
      value: rule.primaryValue,
    })
  }
  if (rule.secondaryField && rule.secondaryOperator && rule.secondaryValue) {
    fallback.push({
      field: rule.secondaryField,
      operator: rule.secondaryOperator,
      value: rule.secondaryValue,
    })
  }
  return fallback
}

function buildSentenceCondition(field?: FieldOption, operator?: OperatorOption, value?: string): string | null {
  if (!field || !operator || !value) {
    return null
  }
  const prettyField = fieldLabels[field] || field
  const prettyOperator = operatorLabels[operator] || operator.toLowerCase()
  return `${prettyField} ${prettyOperator} "${value}"`
}

