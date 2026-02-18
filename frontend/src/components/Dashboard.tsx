import { useEffect, useState } from 'react'
import '../styles/Dashboard.css'

interface DashboardStats {
  averageResponseTime: string
  totalTicketsOpen: number
  pendingClientResponse: number
  pendingInternalResponse: number
  ticketStatuses: Record<string, number>
  ticketContacts: Record<string, number>
  ticketBoards: Record<string, number>
}

interface BreakdownView {
  key: 'status' | 'board' | 'contact'
  title: string
  emptyMessage: string
  entries: Array<[string, number]>
}

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeBreakdownIndex, setActiveBreakdownIndex] = useState(0)

  useEffect(() => {
    const fetchStats = async () => {
      try {
        setLoading(true)
        setError(null)
        const response = await fetch('/api/tickets/stats')
        if (!response.ok) throw new Error('Failed to fetch stats')
        const data = await response.json()
        setStats(data)
      } catch (err: any) {
        setError(err.message || 'Error fetching dashboard stats')
      } finally {
        setLoading(false)
      }
    }
    fetchStats()
  }, [])

  if (loading) {
    return <div className="dashboard-container"><p>Loading...</p></div>
  }
  if (error) {
    return <div className="dashboard-container"><p style={{ color: 'red' }}>Error: {error}</p></div>
  }
  if (!stats) {
    return <div className="dashboard-container"><p>No data available.</p></div>
  }

  const statusEntries = Object.entries(stats.ticketStatuses ?? {}).sort((a, b) => b[1] - a[1])
  const contactEntries = Object.entries(stats.ticketContacts ?? {}).sort((a, b) => b[1] - a[1])
  const boardEntries = Object.entries(stats.ticketBoards ?? {}).sort((a, b) => b[1] - a[1])

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

  const moveBreakdown = (direction: -1 | 1) => {
    setActiveBreakdownIndex((current) => {
      const next = current + direction
      if (next < 0) return breakdownViews.length - 1
      if (next >= breakdownViews.length) return 0
      return next
    })
  }

  return (
    <div className="dashboard-container">
      <h1>Dashboard</h1>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon">RT</div>
          <div className="stat-content">
            <h3>Average Response Time</h3>
            <p className="stat-value">{stats.averageResponseTime}</p>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">OT</div>
          <div className="stat-content">
            <h3>Open Tickets</h3>
            <p className="stat-value">{stats.totalTicketsOpen}</p>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">PC</div>
          <div className="stat-content">
            <h3>Pending Client Response</h3>
            <p className="stat-value">{stats.pendingClientResponse}</p>
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-icon">PI</div>
          <div className="stat-content">
            <h3>Pending Internal Response</h3>
            <p className="stat-value">{stats.pendingInternalResponse}</p>
          </div>
        </div>
      </div>

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
    </div>
  )
}
