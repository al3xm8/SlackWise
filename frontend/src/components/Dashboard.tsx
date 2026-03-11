import { useEffect, useMemo, useState } from 'react'
import '../styles/Dashboard.css'
import CenteredLoadingBar from './CenteredLoadingBar'
import { apiFetch } from '../utils/apiClient'

interface ApiPerson {
  name?: string
  identifier?: string
}

interface ApiNote {
  text?: string
  dateCreated?: string
  timeStart?: string
  internalAnalysisFlag?: boolean
  resolutionFlag?: boolean
  member?: ApiPerson | null
  contact?: ApiPerson | null
}

interface ApiTicket {
  id: number
  summary?: string
  board?: { name?: string }
  status?: { name?: string }
  contact?: ApiPerson | null
  company?: { id?: number; identifier?: string } | null
  type?: { name?: string }
  subType?: { name?: string }
  discussion?: ApiNote[]
}

interface ResponseInterval {
  ticketId: number
  ticketSummary: string
  company: string
  category: string
  subcategory: string
  notePreview: string
  startedAt: number
  endedAt: number
  durationHours: number
  state: 'waiting' | 'resolved'
}

interface TicketImpact {
  ticketId: number
  summary: string
  company: string
  category: string
  subcategory: string
  worstResponseHours: number
  averageResponseHours: number
  waitingResponseHours: number
  waitingCount: number
  intervalCount: number
}

interface DashboardMetrics {
  averageResponseTimeLabel: string
  averageResponseTimeHours: number
  totalTicketsOpen: number
  pendingClientResponse: number
  pendingInternalResponse: number
  ticketStatuses: Record<string, number>
  ticketContacts: Record<string, number>
  ticketBoards: Record<string, number>
  ticketCategories: Record<string, number>
  ticketSubcategories: Record<string, number>
  responseIntervals: ResponseInterval[]
  ticketImpacts: TicketImpact[]
}

interface BreakdownView {
  key: 'status' | 'board' | 'contact'
  title: string
  emptyMessage: string
  entries: Array<[string, number]>
}

const parseEpoch = (value?: string): number => {
  if (!value) return 0
  const millis = Date.parse(value)
  return Number.isNaN(millis) ? 0 : millis
}

const toHours = (ms: number): number => Math.max(0, ms / 3600000)

const sortedEntries = (items: Record<string, number>): Array<[string, number]> => (
  Object.entries(items).sort((a, b) => b[1] - a[1])
)

const companyLabel = (ticket: ApiTicket): string => {
  if (ticket.company?.identifier && ticket.company.identifier.trim().length > 0) {
    return ticket.company.identifier.trim()
  }
  if (typeof ticket.company?.id === 'number') {
    return String(ticket.company.id)
  }
  return 'Unknown company'
}

const noteTimestamp = (entry: ApiNote): number => parseEpoch(entry.dateCreated || entry.timeStart)

const notePreview = (entry: ApiNote): string => {
  const text = typeof entry.text === 'string' ? entry.text.trim() : ''
  if (!text) return 'No note text'
  if (text.length <= 110) return text
  return `${text.slice(0, 107)}...`
}

const formatHours = (value: number): string => `${value.toFixed(2)}h`

const formatTimestamp = (value: number): string => {
  if (!Number.isFinite(value) || value <= 0) return 'Unknown time'
  return new Date(value).toLocaleString()
}

const isInternalEntry = (entry: ApiNote, ticket: ApiTicket): boolean => {
  const memberName = entry.member?.name?.trim() || entry.member?.identifier?.trim()
  if (memberName) {
    return true
  }

  if (entry.internalAnalysisFlag || entry.resolutionFlag) {
    return true
  }

  const entryContact = entry.contact?.name?.trim() || entry.contact?.identifier?.trim()
  const ticketContact = ticket.contact?.name?.trim() || ticket.contact?.identifier?.trim()

  if (!entryContact) {
    return true
  }
  if (!ticketContact) {
    return false
  }

  return entryContact.toLowerCase() !== ticketContact.toLowerCase()
}

const aggregateMetrics = (tickets: ApiTicket[]): DashboardMetrics => {
  let pendingClientResponse = 0
  let pendingInternalResponse = 0

  const ticketStatuses: Record<string, number> = {}
  const ticketContacts: Record<string, number> = {}
  const ticketBoards: Record<string, number> = {}
  const ticketCategories: Record<string, number> = {}
  const ticketSubcategories: Record<string, number> = {}

  const responseIntervals: ResponseInterval[] = []

  for (const ticket of tickets) {
    const statusName = ticket.status?.name?.trim() || 'Unknown'
    const boardName = ticket.board?.name?.trim() || 'Unassigned'
    const contactName = ticket.contact?.name?.trim() || ticket.contact?.identifier?.trim() || 'Unknown'
    const categoryName = ticket.type?.name?.trim() || 'Uncategorized'
    const subcategoryName = ticket.subType?.name?.trim() || 'Unspecified'

    ticketStatuses[statusName] = (ticketStatuses[statusName] ?? 0) + 1
    ticketBoards[boardName] = (ticketBoards[boardName] ?? 0) + 1
    ticketContacts[contactName] = (ticketContacts[contactName] ?? 0) + 1
    ticketCategories[categoryName] = (ticketCategories[categoryName] ?? 0) + 1
    ticketSubcategories[subcategoryName] = (ticketSubcategories[subcategoryName] ?? 0) + 1

    const discussion = [...(ticket.discussion ?? [])]
      .filter((entry) => noteTimestamp(entry) > 0)
      .sort((a, b) => noteTimestamp(a) - noteTimestamp(b))

    if (discussion.length === 0) {
      pendingInternalResponse += 1
      continue
    }

    const lastEntry = discussion[discussion.length - 1]
    if (isInternalEntry(lastEntry, ticket)) {
      pendingClientResponse += 1
    } else {
      pendingInternalResponse += 1
    }

    let openClientEntry: ApiNote | null = null
    const now = Date.now()

    for (const entry of discussion) {
      const entryTs = noteTimestamp(entry)
      const internal = isInternalEntry(entry, ticket)

      if (!internal) {
        if (!openClientEntry) {
          openClientEntry = entry
        }
        continue
      }

      if (openClientEntry) {
        const startedAt = noteTimestamp(openClientEntry)
        const durationHours = toHours(entryTs - startedAt)
        responseIntervals.push({
          ticketId: ticket.id,
          ticketSummary: ticket.summary?.trim() || 'No summary',
          company: companyLabel(ticket),
          category: categoryName,
          subcategory: subcategoryName,
          notePreview: notePreview(openClientEntry),
          startedAt,
          endedAt: entryTs,
          durationHours,
          state: 'resolved',
        })
        openClientEntry = null
      }
    }

    if (openClientEntry) {
      const startedAt = noteTimestamp(openClientEntry)
      const durationHours = toHours(now - startedAt)
      responseIntervals.push({
        ticketId: ticket.id,
        ticketSummary: ticket.summary?.trim() || 'No summary',
        company: companyLabel(ticket),
        category: categoryName,
        subcategory: subcategoryName,
        notePreview: notePreview(openClientEntry),
        startedAt,
        endedAt: now,
        durationHours,
        state: 'waiting',
      })
    }
  }

  const totalHours = responseIntervals.reduce((sum, interval) => sum + interval.durationHours, 0)
  const averageResponseTimeHours = responseIntervals.length > 0 ? totalHours / responseIntervals.length : 0

  const ticketImpactMap = new Map<number, TicketImpact>()

  for (const interval of responseIntervals) {
    const existing = ticketImpactMap.get(interval.ticketId)
    if (!existing) {
      ticketImpactMap.set(interval.ticketId, {
        ticketId: interval.ticketId,
        summary: interval.ticketSummary,
        company: interval.company,
        category: interval.category,
        subcategory: interval.subcategory,
        worstResponseHours: interval.durationHours,
        averageResponseHours: interval.durationHours,
        waitingResponseHours: interval.state === 'waiting' ? interval.durationHours : 0,
        waitingCount: interval.state === 'waiting' ? 1 : 0,
        intervalCount: 1,
      })
      continue
    }

    existing.worstResponseHours = Math.max(existing.worstResponseHours, interval.durationHours)
    existing.intervalCount += 1
    existing.averageResponseHours = (
      ((existing.averageResponseHours * (existing.intervalCount - 1)) + interval.durationHours) /
      existing.intervalCount
    )
    if (interval.state === 'waiting') {
      existing.waitingCount += 1
      existing.waitingResponseHours += interval.durationHours
    }
  }

  const ticketImpacts = Array.from(ticketImpactMap.values())
    .sort((a, b) => b.waitingResponseHours - a.waitingResponseHours || b.worstResponseHours - a.worstResponseHours)

  return {
    averageResponseTimeLabel: `${averageResponseTimeHours.toFixed(2)} hours`,
    averageResponseTimeHours,
    totalTicketsOpen: tickets.length,
    pendingClientResponse,
    pendingInternalResponse,
    ticketStatuses,
    ticketContacts,
    ticketBoards,
    ticketCategories,
    ticketSubcategories,
    responseIntervals,
    ticketImpacts,
  }
}

export default function Dashboard() {
  const [tickets, setTickets] = useState<ApiTicket[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeBreakdownIndex, setActiveBreakdownIndex] = useState(0)
  const [selectedCompany, setSelectedCompany] = useState('__all__')
  const [showHeavyPanel, setShowHeavyPanel] = useState(false)

  useEffect(() => {
    const fetchTickets = async () => {
      try {
        setLoading(true)
        setError(null)
        const response = await apiFetch('/api/tickets/open')
        if (!response.ok) throw new Error('Failed to fetch dashboard ticket data')
        const data = await response.json()
        setTickets(Array.isArray(data) ? data : [])
      } catch (err: unknown) {
        if (err instanceof Error) {
          setError(err.message)
        } else {
          setError('Error fetching dashboard data')
        }
      } finally {
        setLoading(false)
      }
    }

    fetchTickets()
  }, [])

  const companyOptions = useMemo(() => (
    Array.from(new Set(tickets.map(companyLabel))).sort((a, b) => a.localeCompare(b))
  ), [tickets])

  useEffect(() => {
    if (selectedCompany !== '__all__' && !companyOptions.includes(selectedCompany)) {
      setSelectedCompany('__all__')
    }
  }, [companyOptions, selectedCompany])

  const filteredTickets = useMemo(() => {
    if (selectedCompany === '__all__') return tickets
    return tickets.filter((ticket) => companyLabel(ticket) === selectedCompany)
  }, [tickets, selectedCompany])

  const metrics = useMemo(() => aggregateMetrics(filteredTickets), [filteredTickets])

  const statusEntries = useMemo(() => sortedEntries(metrics.ticketStatuses), [metrics.ticketStatuses])
  const contactEntries = useMemo(() => sortedEntries(metrics.ticketContacts), [metrics.ticketContacts])
  const boardEntries = useMemo(() => sortedEntries(metrics.ticketBoards), [metrics.ticketBoards])
  const categoryEntries = useMemo(() => sortedEntries(metrics.ticketCategories), [metrics.ticketCategories])
  const subcategoryEntries = useMemo(() => sortedEntries(metrics.ticketSubcategories), [metrics.ticketSubcategories])

  const breakdownViews: BreakdownView[] = [
    {
      key: 'status',
      title: 'Ticket Status Breakdown',
      emptyMessage: 'No status data available.',
      entries: statusEntries,
    },
    {
      key: 'board',
      title: 'Tickets By Board',
      emptyMessage: 'No board data available.',
      entries: boardEntries,
    },
    {
      key: 'contact',
      title: 'Tickets By Contact',
      emptyMessage: 'No contact data available.',
      entries: contactEntries,
    },
  ]

  const activeView = breakdownViews[activeBreakdownIndex] ?? breakdownViews[0]
  const maxCount = activeView.entries.reduce((max, [, count]) => Math.max(max, count), 0)

  const worstTickets = useMemo(() => metrics.ticketImpacts.slice(0, 10), [metrics.ticketImpacts])

  const activeWaitingNotes = useMemo(() => (
    [...metrics.responseIntervals]
      .filter((interval) => interval.state === 'waiting')
      .sort((a, b) => b.durationHours - a.durationHours)
      .slice(0, 10)
  ), [metrics.responseIntervals])

  const worstNotes = useMemo(() => (
    [...metrics.responseIntervals]
      .sort((a, b) => b.durationHours - a.durationHours)
      .slice(0, 10)
  ), [metrics.responseIntervals])

  const moveBreakdown = (direction: -1 | 1) => {
    setActiveBreakdownIndex((current) => {
      const next = current + direction
      if (next < 0) return breakdownViews.length - 1
      if (next >= breakdownViews.length) return 0
      return next
    })
  }

  if (loading) {
    return <CenteredLoadingBar label="Loading dashboard..." />
  }
  if (error) {
    return <div className="dashboard-container"><p style={{ color: 'red' }}>Error: {error}</p></div>
  }

  return (
    <div className="dashboard-container">
      <h1>Dashboard</h1>

      <div className="dashboard-toolbar">
        <label className="company-filter" htmlFor="dashboardCompanyFilter">
          <span>Company</span>
          <select
            id="dashboardCompanyFilter"
            value={selectedCompany}
            onChange={(event) => setSelectedCompany(event.target.value)}
          >
            <option value="__all__">All tracked companies</option>
            {companyOptions.map((company) => (
              <option key={company} value={company}>{company}</option>
            ))}
          </select>
        </label>
        <div className="toolbar-meta">{filteredTickets.length} open tickets in view</div>
      </div>

      <div className="stats-grid">
        <button
          type="button"
          className={`stat-card stat-card-button${showHeavyPanel ? ' active' : ''}`}
          onClick={() => setShowHeavyPanel((current) => !current)}
        >
          <div className="stat-icon">RT</div>
          <div className="stat-content">
            <h3>Average Response Time</h3>
            <p className="stat-value">{metrics.averageResponseTimeLabel}</p>
            <p className="stat-subtext">Click to see worst response-time offenders</p>
          </div>
        </button>

        <div className="stat-card">
          <div className="stat-icon">OT</div>
          <div className="stat-content">
            <h3>Open Tickets</h3>
            <p className="stat-value">{metrics.totalTicketsOpen}</p>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">PC</div>
          <div className="stat-content">
            <h3>Pending Client Response</h3>
            <p className="stat-value">{metrics.pendingClientResponse}</p>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon">PI</div>
          <div className="stat-content">
            <h3>Pending Internal Response</h3>
            <p className="stat-value">{metrics.pendingInternalResponse}</p>
          </div>
        </div>
      </div>

      {showHeavyPanel ? (
        <section className="dashboard-section drilldown-panel">
          <h2>Response-Time Impact Drill-down</h2>
          <p className="drilldown-description">
            Ranked by longest client wait intervals, which hurt response-time metrics the most.
          </p>
          <div className="heavy-grid">
            <div>
              <h3>Worst Tickets (Response Delay)</h3>
              <ul className="heavy-list">
                {worstTickets.length === 0 ? <li className="empty-breakdown">No response data in this view.</li> : null}
                {worstTickets.map((ticket) => (
                  <li key={`ticket-impact-${ticket.ticketId}`} className="heavy-item">
                    <div className="heavy-title">#{ticket.ticketId} {ticket.summary}</div>
                    <div className="heavy-meta">
                      {ticket.company} • worst {formatHours(ticket.worstResponseHours)} • avg {formatHours(ticket.averageResponseHours)}
                    </div>
                    <div className="heavy-meta">
                      {ticket.waitingCount > 0
                        ? `currently waiting ${formatHours(ticket.waitingResponseHours)} across ${ticket.waitingCount} open item(s)`
                        : 'no current open client waits (historical delay impact)'}
                    </div>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <h3>Open Wait Items (Now)</h3>
              <ul className="heavy-list">
                {activeWaitingNotes.length === 0 ? <li className="empty-breakdown">No active client-wait items right now.</li> : null}
                {activeWaitingNotes.map((item, index) => (
                  <li key={`active-wait-${item.ticketId}-${item.startedAt}-${index}`} className="heavy-item">
                    <div className="heavy-title">#{item.ticketId} {item.ticketSummary}</div>
                    <div className="heavy-meta">
                      {item.company} • waiting {formatHours(item.durationHours)}
                    </div>
                    <div className="heavy-meta">Opened: {formatTimestamp(item.startedAt)}</div>
                    <div className="heavy-meta">Note: {item.notePreview}</div>
                  </li>
                ))}
              </ul>

              <h3 style={{ marginTop: '1rem' }}>Worst Historical Note Delays</h3>
              <ul className="heavy-list">
                {worstNotes.length === 0 ? <li className="empty-breakdown">No response intervals in this view.</li> : null}
                {worstNotes.map((item, index) => (
                  <li key={`note-impact-${item.ticketId}-${item.startedAt}-${index}`} className="heavy-item">
                    <div className="heavy-title">#{item.ticketId} {item.ticketSummary}</div>
                    <div className="heavy-meta">
                      {item.company} • {formatHours(item.durationHours)} • {item.state === 'waiting' ? 'still waiting' : 'resolved'}
                    </div>
                    <div className="heavy-meta">Opened: {formatTimestamp(item.startedAt)}</div>
                    <div className="heavy-meta">Note: {item.notePreview}</div>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>
      ) : null}

      <div className="breakdown-grid">
        <div className="dashboard-section">
          <div className="breakdown-header">
            <button
              type="button"
              className="breakdown-nav-btn"
              aria-label="Previous breakdown view"
              onClick={() => moveBreakdown(-1)}
            >
              &#8592;
            </button>
            <h2>{activeView.title}</h2>
            <button
              type="button"
              className="breakdown-nav-btn"
              aria-label="Next breakdown view"
              onClick={() => moveBreakdown(1)}
            >
              &#8594;
            </button>
          </div>

          <div className="breakdown-indicators" aria-hidden="true">
            {breakdownViews.map((view, index) => (
              <span
                key={view.key}
                className={`breakdown-indicator${index === activeBreakdownIndex ? ' active' : ''}`}
              />
            ))}
          </div>

          {activeView.entries.length === 0 ? (
            <p className="empty-breakdown">{activeView.emptyMessage}</p>
          ) : (
            <div className="bar-chart">
              {activeView.entries.map(([label, count]) => {
                const widthPercent = maxCount > 0 ? (count / maxCount) * 100 : 0
                return (
                  <div className="bar-row" key={`${activeView.key}-${label}`}>
                    <div className="bar-label" title={label}>{label}</div>
                    <div className="bar-track">
                      <div className="bar-fill" style={{ width: `${widthPercent}%` }} />
                    </div>
                    <div className="bar-value">{count}</div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      <div className="bottom-grid">
        <section className="dashboard-section">
          <h2>Tickets by Category</h2>
          {categoryEntries.length === 0 ? (
            <p className="empty-breakdown">No category data available.</p>
          ) : (
            <div className="bar-chart">
              {categoryEntries.map(([label, count]) => {
                const maxCategoryCount = categoryEntries[0]?.[1] ?? 1
                const widthPercent = (count / maxCategoryCount) * 100
                return (
                  <div className="bar-row" key={`category-${label}`}>
                    <div className="bar-label" title={label}>{label}</div>
                    <div className="bar-track">
                      <div className="bar-fill" style={{ width: `${widthPercent}%` }} />
                    </div>
                    <div className="bar-value">{count}</div>
                  </div>
                )
              })}
            </div>
          )}
        </section>

        <section className="dashboard-section">
          <h2>Tickets by Subcategory</h2>
          {subcategoryEntries.length === 0 ? (
            <p className="empty-breakdown">No subcategory data available.</p>
          ) : (
            <div className="bar-chart">
              {subcategoryEntries.map(([label, count]) => {
                const maxSubcategoryCount = subcategoryEntries[0]?.[1] ?? 1
                const widthPercent = (count / maxSubcategoryCount) * 100
                return (
                  <div className="bar-row" key={`subcategory-${label}`}>
                    <div className="bar-label" title={label}>{label}</div>
                    <div className="bar-track">
                      <div className="bar-fill" style={{ width: `${widthPercent}%` }} />
                    </div>
                    <div className="bar-value">{count}</div>
                  </div>
                )
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}




