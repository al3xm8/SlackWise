import { useState } from 'react'
import '../styles/Rules.css'

interface Condition {
  id: string
  field: string
  operator: string
  value: string
}

interface ConditionGroup {
  id: string
  logic: 'AND' | 'OR'
  conditions: Condition[]
}

interface Rule {
  id: string
  name: string
  conditionGroups: ConditionGroup[]
  destination: string
  channel: string
  assignTo: string
  enabled: boolean
}

const AVAILABLE_FIELDS = [
  'Priority',
  'Type',
  'Status',
  'Company',
  'Category',
  'Summary',
  'Assigned To'
]

const OPERATORS = ['equals', 'contains', 'starts with', 'ends with', 'greater than', 'less than']

const createEmptyFormData = () => ({
  name: '',
  conditionGroups: [
    {
      id: 'cg-' + Date.now(),
      logic: 'AND' as const,
      conditions: [
        { id: 'c-' + Date.now(), field: 'Priority', operator: 'equals', value: '' }
      ]
    }
  ],
  destination: 'Slack',
  channel: '',
  assignTo: ''
})

const cloneConditionGroups = (groups: ConditionGroup[]) =>
  groups.map((group) => ({
    id: group.id,
    logic: group.logic,
    conditions: group.conditions.map((condition) => ({ ...condition }))
  }))

export default function Rules() {
  const [rules, setRules] = useState<Rule[]>([
    {
      id: '1',
      name: 'High Priority to Urgent',
      conditionGroups: [
        {
          id: 'cg1',
          logic: 'AND',
          conditions: [
            { id: 'c1', field: 'Priority', operator: 'equals', value: 'High' }
          ]
        }
      ],
      destination: 'Slack',
      channel: '#urgent',
      assignTo: '',
      enabled: true
    }
  ])

  const [showModal, setShowModal] = useState(false)
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null)
  const [formData, setFormData] = useState<{
    name: string
    conditionGroups: ConditionGroup[]
    destination: string
    channel: string
    assignTo: string
  }>(createEmptyFormData())

  const handleSaveRule = () => {
    if (!formData.name || !formData.channel) {
      alert('Please fill in rule name and channel')
      return
    }

    if (editingRuleId) {
      setRules(rules.map(rule =>
        rule.id === editingRuleId
          ? {
              ...rule,
              name: formData.name,
              conditionGroups: formData.conditionGroups,
              destination: formData.destination,
              channel: formData.channel,
              assignTo: formData.assignTo
            }
          : rule
      ))
    } else {
      const newRule: Rule = {
        id: Date.now().toString(),
        name: formData.name,
        conditionGroups: formData.conditionGroups,
        destination: formData.destination,
        channel: formData.channel,
        assignTo: formData.assignTo,
        enabled: true
      }
      setRules([...rules, newRule])
    }

    setFormData(createEmptyFormData())
    setEditingRuleId(null)
    setShowModal(false)
  }

  const toggleRule = (id: string) => {
    setRules(rules.map(rule =>
      rule.id === id ? { ...rule, enabled: !rule.enabled } : rule
    ))
  }

  const deleteRule = (id: string) => {
    setRules(rules.filter(rule => rule.id !== id))
  }

  const handleNewRule = () => {
    setEditingRuleId(null)
    setFormData(createEmptyFormData())
    setShowModal(true)
  }

  const handleEditRule = (rule: Rule) => {
    setEditingRuleId(rule.id)
    setFormData({
      name: rule.name,
      conditionGroups: cloneConditionGroups(rule.conditionGroups),
      destination: rule.destination,
      channel: rule.channel,
      assignTo: rule.assignTo
    })
    setShowModal(true)
  }

  const handleCloseModal = () => {
    setShowModal(false)
    setEditingRuleId(null)
    setFormData(createEmptyFormData())
  }

  const addConditionGroup = () => {
    const newGroups: ConditionGroup[] = [...formData.conditionGroups]
    newGroups.push({
      id: 'cg-' + Date.now(),
      logic: 'AND',
      conditions: [
        { id: 'c-' + Date.now(), field: 'Priority', operator: 'equals', value: '' }
      ]
    })
    setFormData({ ...formData, conditionGroups: newGroups })
  }

  const addCondition = (groupIndex: number) => {
    const newGroups = [...formData.conditionGroups]
    newGroups[groupIndex].conditions.push({
      id: 'c-' + Date.now(),
      field: 'Priority',
      operator: 'equals',
      value: ''
    })
    setFormData({ ...formData, conditionGroups: newGroups })
  }

  const removeCondition = (groupIndex: number, conditionIndex: number) => {
    const newGroups = [...formData.conditionGroups]
    newGroups[groupIndex].conditions.splice(conditionIndex, 1)
    if (newGroups[groupIndex].conditions.length === 0 && newGroups.length > 1) {
      newGroups.splice(groupIndex, 1)
    }
    setFormData({ ...formData, conditionGroups: newGroups })
  }

  const updateCondition = (groupIndex: number, conditionIndex: number, field: string, value: string) => {
    const newGroups = [...formData.conditionGroups]
    const condition = newGroups[groupIndex].conditions[conditionIndex]
    if (field === 'field') condition.field = value
    if (field === 'operator') condition.operator = value
    if (field === 'value') condition.value = value
    setFormData({ ...formData, conditionGroups: newGroups })
  }

  const updateGroupLogic = (groupIndex: number, logic: 'AND' | 'OR') => {
    const newGroups = [...formData.conditionGroups]
    newGroups[groupIndex].logic = logic
    setFormData({ ...formData, conditionGroups: newGroups })
  }

  const renderConditionGroups = (groups: ConditionGroup[], isPreview = false) => {
    return (
      <div className="condition-groups">
        {groups.map((group, groupIndex) => (
          <div key={group.id} className="condition-group">
            {groupIndex > 0 && <div className="group-separator">{groups[groupIndex - 1].logic}</div>}
            
            <div className="group-content">
              {!isPreview && (
                <div className="group-logic-selector">
                  <select
                    value={group.logic}
                    onChange={(e) => updateGroupLogic(groupIndex, e.target.value as 'AND' | 'OR')}
                    className="logic-select"
                  >
                    <option value="AND">AND</option>
                    <option value="OR">OR</option>
                  </select>
                </div>
              )}

              <div className="conditions">
                {group.conditions.map((condition, conditionIndex) => (
                  <div key={condition.id} className="condition-item">
                    {conditionIndex > 0 && <div className="condition-separator">{group.logic}</div>}
                    
                    <div className="condition-inputs">
                      {!isPreview ? (
                        <>
                          <select
                            value={condition.field}
                            onChange={(e) => updateCondition(groupIndex, conditionIndex, 'field', e.target.value)}
                            className="condition-select"
                          >
                            {AVAILABLE_FIELDS.map(field => (
                              <option key={field} value={field}>{field}</option>
                            ))}
                          </select>

                          <select
                            value={condition.operator}
                            onChange={(e) => updateCondition(groupIndex, conditionIndex, 'operator', e.target.value)}
                            className="condition-select"
                          >
                            {OPERATORS.map(op => (
                              <option key={op} value={op}>{op}</option>
                            ))}
                          </select>

                          <input
                            type="text"
                            value={condition.value}
                            onChange={(e) => updateCondition(groupIndex, conditionIndex, 'value', e.target.value)}
                            placeholder="Value"
                            className="condition-input"
                          />

                          <button
                            onClick={() => removeCondition(groupIndex, conditionIndex)}
                            className="remove-condition-btn"
                            title="Remove condition"
                          >
                            ×
                          </button>
                        </>
                      ) : (
                        <div className="condition-preview">
                          <span
                            className={`preview-field${
                              condition.field === 'Priority' ? ' preview-emphasis' : ''
                            }`}
                          >
                            {condition.field}
                          </span>
                          <span className="preview-operator">{condition.operator}</span>
                          <span className="preview-value">{condition.value}</span>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {!isPreview && (
                <button
                  onClick={() => addCondition(groupIndex)}
                  className="add-condition-btn"
                >
                  + Add Condition
                </button>
              )}
            </div>
          </div>
        ))}

        {!isPreview && (
          <button onClick={addConditionGroup} className="add-group-btn">
            + Add Condition Group
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="rules">
      <div className="rules-header">
        <div>
          <h2>Routing Rules</h2>
          <p>Create rules to automatically route tickets to Slack channels</p>
        </div>
        <button className="btn-primary" onClick={handleNewRule}>
          + New Rule
        </button>
      </div>

      <div className="rules-list">
        {rules.length === 0 ? (
          <div className="empty-state">
            <p>No rules created yet. Create your first rule to get started.</p>
          </div>
        ) : (
          rules.map((rule) => (
            <div key={rule.id} className="rule-card">
              <div className="rule-header">
                <div className="rule-title">
                  <h3>{rule.name}</h3>
                  <span className={`status-badge ${rule.enabled ? 'enabled' : 'disabled'}`}>
                    {rule.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </div>
                <div className="rule-actions">
                  <button
                    className="toggle-btn"
                    onClick={() => toggleRule(rule.id)}
                    title={rule.enabled ? 'Disable rule' : 'Enable rule'}
                  >
                    {rule.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button
                    className="edit-btn"
                    onClick={() => handleEditRule(rule)}
                    title="Edit rule"
                  >
                    Edit
                  </button>
                  <button
                    className="delete-btn"
                    onClick={() => deleteRule(rule.id)}
                    title="Delete rule"
                  >
                    Delete
                  </button>
                </div>
              </div>

              <div className="rule-details">
                <div className="conditions-preview">
                  {renderConditionGroups(rule.conditionGroups, true)}
                </div>
                <div className="rule-destination">
                  <span className="label">Routes to:</span>
                  <span className="value">{rule.destination} — {rule.channel}</span>
                </div>
                {rule.assignTo && (
                  <div className="rule-destination">
                    <span className="label">Assign to:</span>
                    <span className="value">{rule.assignTo}</span>
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Modal for creating new rule */}
      {showModal && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingRuleId ? 'Edit Rule' : 'Create New Rule'}</h2>
              <button className="close-btn" onClick={handleCloseModal}>×</button>
            </div>

            <div className="modal-body">
              <div className="form-group">
                <label>Rule Name *</label>
                <input
                  type="text"
                  placeholder="e.g., High Priority to Urgent"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Conditions *</label>
                {renderConditionGroups(formData.conditionGroups)}
              </div>

              <div className="form-group">
                <label>Destination *</label>
                <select
                  value={formData.destination}
                  onChange={(e) => setFormData({ ...formData, destination: e.target.value })}
                >
                  <option value="Slack">Slack</option>
                </select>
              </div>

              <div className="form-group">
                <label>Channel *</label>
                <input
                  type="text"
                  placeholder="e.g., #urgent or #general"
                  value={formData.channel}
                  onChange={(e) => setFormData({ ...formData, channel: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label>Assign To (optional)</label>
                <input
                  type="text"
                  placeholder="e.g., Alex Johnson"
                  value={formData.assignTo}
                  onChange={(e) => setFormData({ ...formData, assignTo: e.target.value })}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button
                className="btn-secondary"
                onClick={handleCloseModal}
              >
                Cancel
              </button>
              <button
                className="btn-primary"
                onClick={handleSaveRule}
              >
                {editingRuleId ? 'Save Changes' : 'Create Rule'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
