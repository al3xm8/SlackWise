import { useCallback, useEffect, useMemo, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import '../styles/CatchUp.css'
import { uiDebug, uiError, uiWarn } from '../utils/uiDebug'
import CenteredLoadingBar from './CenteredLoadingBar'
import { apiFetch } from '../utils/apiClient'

interface TicketResponse {
  id: number
  author: string
  content: string
  timestamp: string
  type: 'response' | 'note'
}

interface TicketData {
  id: number
  summary: string
  status: string
  priority: string
  board: string
  company: string
  owner: string
  contact: string
  lastUpdate: string
  lastUpdateTs: number
  responses: TicketResponse[]
}

interface ApiPerson {
  name?: string
  identifier?: string
}

interface ApiNote {
  id?: number
  text?: string
  dateCreated?: string
  timeStart?: string
  member?: ApiPerson | null
  contact?: ApiPerson | null
}

interface ApiTicket {
  contact?: ApiPerson | null
  id: number
  summary?: string
  board?: { name?: string }
  status?: { name?: string }
  priority?: { name?: string }
  company?: { identifier?: string }
  owner?: { identifier?: string }
  discussion?: ApiNote[]
}

type FilterMode = 'include' | 'exclude'
type SortOption = 'lastUpdatedDesc' | 'lastUpdatedAsc' | 'priorityHighToLow' | 'priorityLowToHigh' | 'ticketIdDesc' | 'ticketIdAsc'

const sortedUnique = (values: string[]): string[] => (
  Array.from(new Set(values.filter((value) => value && value.trim().length > 0)))
    .sort((a, b) => a.localeCompare(b))
)

const parseEpoch = (value?: string): number => {
  if (!value) return 0
  const millis = Date.parse(value)
  return Number.isNaN(millis) ? 0 : millis
}

const getPrioritySortValue = (priority: string): number => {
  const numberMatch = priority.match(/\d+/)
  if (numberMatch) return Number(numberMatch[0])

  const normalized = priority.toLowerCase()
  if (normalized.includes('critical') || normalized.includes('high')) return 1
  if (normalized.includes('medium')) return 2
  if (normalized.includes('low')) return 3
  return 99
}

const formatRelativeTime = (value?: string): string => {
  const millis = parseEpoch(value)
  if (millis === 0) return 'Unknown'

  const deltaMs = Date.now() - millis
  const minutes = Math.floor(deltaMs / 60000)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`
  return `${days} day${days === 1 ? '' : 's'} ago`
}

const renderInlineMarkdown = (text: string): ReactNode[] => {
  const nodes: ReactNode[] = []
  const tokenPattern = /(\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|\*\*([^*]+)\*\*|~~([^~]+)~~|`([^`]+)`|\*([^*\n]+)\*)/g

  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = tokenPattern.exec(text)) !== null) {
    const [fullMatch] = match
    const start = match.index
    const end = start + fullMatch.length

    if (start > lastIndex) {
      nodes.push(text.slice(lastIndex, start))
    }

    if (match[2] && match[3]) {
      const label = match[2]
      const url = match[3]
      nodes.push(
        <a key={`${start}-${end}`} href={url} target="_blank" rel="noreferrer">
          {label}
        </a>,
      )
    } else if (match[4]) {
      nodes.push(<strong key={`${start}-${end}`}>{match[4]}</strong>)
    } else if (match[5]) {
      nodes.push(<del key={`${start}-${end}`}>{match[5]}</del>)
    } else if (match[6]) {
      nodes.push(<code key={`${start}-${end}`}>{match[6]}</code>)
    } else if (match[7]) {
      nodes.push(<em key={`${start}-${end}`}>{match[7]}</em>)
    } else {
      nodes.push(fullMatch)
    }

    lastIndex = end
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex))
  }

  return nodes
}

const renderMarkdownText = (text: string): ReactNode => {
  const lines = text.split(/\r?\n/)
  return lines.map((line, index) => (
    <span key={`line-${index}`}>
      {renderInlineMarkdown(line)}
      {index < lines.length - 1 ? <br /> : null}
    </span>
  ))
}

const mapTicket = (ticket: ApiTicket): TicketData => {
  const rawDiscussion = ticket.discussion ?? []
  const contactName = ticket.contact?.name || ticket.contact?.identifier || 'Unknown'

  type TicketResponseWithTs = TicketResponse & { _ts: number }

  const responsesWithTs: TicketResponseWithTs[] = rawDiscussion
    .filter((note) => typeof note.text === 'string' && note.text.trim().length > 0)
    .map((note, idx) => {
      const sourceTime = note.dateCreated || note.timeStart
      const author = note.member?.name || note.contact?.name || 'Unknown'
      const responseType: TicketResponse['type'] = note.member?.name ? 'response' : 'note'
      return {
        id: note.id ?? idx + 1,
        author,
        content: note.text!.trim(),
        timestamp: formatRelativeTime(sourceTime),
        type: responseType,
        _ts: parseEpoch(sourceTime),
      }
    })
    .sort((a: TicketResponseWithTs, b: TicketResponseWithTs) => b._ts - a._ts)

  const responses: TicketResponse[] = responsesWithTs.map((item) => ({
    id: item.id,
    author: item.author,
    content: item.content,
    timestamp: item.timestamp,
    type: item.type,
  }))
  const latestUpdateTs = responsesWithTs.length > 0 ? responsesWithTs[0]._ts : 0

  return {
    id: ticket.id,
    summary: ticket.summary || 'No Summary',
    status: ticket.status?.name || 'Unknown',
    priority: ticket.priority?.name || 'Unspecified',
    board: ticket.board?.name || 'Unassigned',
    company: ticket.company?.identifier || 'Unknown',
    owner: contactName,
    contact: contactName,
    lastUpdate: responses.length > 0 ? responses[0].timestamp : 'No updates yet',
    lastUpdateTs: latestUpdateTs,
    responses,
  }
}

export default function CatchUp() {
  const [tickets, setTickets] = useState<TicketData[]>([])
  const [selectedTicket, setSelectedTicket] = useState<TicketData | null>(null)
  const [loading, setLoading] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [actionLoading, setActionLoading] = useState<null | 'response' | 'status'>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [filtersCollapsed, setFiltersCollapsed] = useState(false)
  const [isResponseModalOpen, setIsResponseModalOpen] = useState(false)
  const [isStatusModalOpen, setIsStatusModalOpen] = useState(false)
  const [responseText, setResponseText] = useState('')
  const [responseInternal, setResponseInternal] = useState(false)
  const [responseResolution, setResponseResolution] = useState(false)
  const [statusSelection, setStatusSelection] = useState('')
  const [sortOption, setSortOption] = useState<SortOption>('lastUpdatedDesc')
  const [filterMode, setFilterMode] = useState<FilterMode>('include')
  const [selectedBoards, setSelectedBoards] = useState<string[]>([])
  const [selectedPriorities, setSelectedPriorities] = useState<string[]>([])
  const [selectedContacts, setSelectedContacts] = useState<string[]>([])
  const [selectedCompanies, setSelectedCompanies] = useState<string[]>([])

  const fetchTickets = useCallback(async (showPageLoader: boolean) => {
    try {
      uiDebug('CatchUp', `Starting ticket fetch (${showPageLoader ? 'initial' : 'resync'})`)
      if (showPageLoader) {
        setLoading(true)
      } else {
        setSyncing(true)
      }
      setLoadError(null)

      const response = await apiFetch('/api/tickets/open')
      if (!response.ok) {
        throw new Error('Failed to fetch open tickets')
      }

      const data: ApiTicket[] = await response.json()
      const mapped = data.map(mapTicket)
      uiDebug('CatchUp', 'Ticket fetch successful', { count: mapped.length })

      setTickets(mapped)
      setSelectedTicket((current) => {
        if (!current) return mapped[0] ?? null
        return mapped.find((ticket) => ticket.id === current.id) ?? mapped[0] ?? null
      })
    } catch (err: unknown) {
      if (err instanceof Error) {
        uiError('CatchUp', 'Ticket fetch failed', { message: err.message })
        setLoadError(err.message)
      } else {
        uiError('CatchUp', 'Ticket fetch failed with unknown error')
        setLoadError('Error fetching tickets')
      }
    } finally {
      uiDebug('CatchUp', `Ticket fetch finished (${showPageLoader ? 'initial' : 'resync'})`)
      if (showPageLoader) {
        setLoading(false)
      } else {
        setSyncing(false)
      }
    }
  }, [])

  useEffect(() => {
    fetchTickets(true)
  }, [fetchTickets])

  const boardOptions = useMemo(() => sortedUnique(tickets.map((ticket) => ticket.board)), [tickets])
  const priorityOptions = useMemo(() => sortedUnique(tickets.map((ticket) => ticket.priority)), [tickets])
  const contactOptions = useMemo(() => sortedUnique(tickets.map((ticket) => ticket.contact)), [tickets])
  const companyOptions = useMemo(() => sortedUnique(tickets.map((ticket) => ticket.company)), [tickets])
  const statusOptions = useMemo(() => sortedUnique(tickets.map((ticket) => ticket.status)), [tickets])

  const getSelectedValues = (event: ChangeEvent<HTMLSelectElement>): string[] => (
    Array.from(event.target.selectedOptions, (option) => option.value)
  )

  const matchesFilter = useCallback((value: string, selectedValues: string[]) => {
    if (selectedValues.length === 0) return true
    const isSelected = selectedValues.includes(value)
    return filterMode === 'include' ? isSelected : !isSelected
  }, [filterMode])

  const filteredTickets = useMemo(() => (
    tickets.filter((ticket) => (
      matchesFilter(ticket.board, selectedBoards)
      && matchesFilter(ticket.priority, selectedPriorities)
      && matchesFilter(ticket.contact, selectedContacts)
      && matchesFilter(ticket.company, selectedCompanies)
    ))
  ), [
    tickets,
    matchesFilter,
    selectedBoards,
    selectedPriorities,
    selectedContacts,
    selectedCompanies,
  ])

  const searchedTickets = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    if (query.length === 0) return filteredTickets

    return filteredTickets.filter((ticket) => {
      const searchableText = [
        ticket.id.toString(),
        ticket.summary,
        ticket.status,
        ticket.priority,
        ticket.board,
        ticket.company,
        ticket.owner,
        ticket.contact,
      ].join(' ').toLowerCase()

      return searchableText.includes(query)
    })
  }, [filteredTickets, searchQuery])

  const sortedTickets = useMemo(() => {
    const items = [...searchedTickets]
    items.sort((a, b) => {
      if (sortOption === 'lastUpdatedDesc') return b.lastUpdateTs - a.lastUpdateTs
      if (sortOption === 'lastUpdatedAsc') return a.lastUpdateTs - b.lastUpdateTs
      if (sortOption === 'priorityHighToLow') return getPrioritySortValue(a.priority) - getPrioritySortValue(b.priority)
      if (sortOption === 'priorityLowToHigh') return getPrioritySortValue(b.priority) - getPrioritySortValue(a.priority)
      if (sortOption === 'ticketIdAsc') return a.id - b.id
      return b.id - a.id
    })
    return items
  }, [searchedTickets, sortOption])

  useEffect(() => {
    setSelectedTicket((current) => {
      if (sortedTickets.length === 0) return null
      if (!current) return sortedTickets[0]
      return sortedTickets.find((ticket) => ticket.id === current.id) ?? sortedTickets[0]
    })
  }, [sortedTickets])

  const clearFilters = () => {
    setSelectedBoards([])
    setSelectedPriorities([])
    setSelectedContacts([])
    setSelectedCompanies([])
  }

  const getPriorityClass = (priority: string) => {
    return `priority-${priority.toLowerCase().replace(' ', '-')}`
  }

  const parseErrorMessage = async (response: Response, fallback: string) => {
    try {
      const body = await response.json()
      if (typeof body?.error === 'string' && body.error.trim().length > 0) {
        return body.error
      }
      if (typeof body?.message === 'string' && body.message.trim().length > 0) {
        return body.message
      }
    } catch {
      // ignore parse errors and use fallback
    }
    return fallback
  }

  const openResponseModal = () => {
    if (!selectedTicket) return
    uiDebug('CatchUp', 'Opening Add Response modal', { ticketId: selectedTicket.id })
    setResponseText('')
    setResponseInternal(false)
    setResponseResolution(false)
    setActionError(null)
    setIsResponseModalOpen(true)
  }

  const openStatusModal = () => {
    if (!selectedTicket) return
    uiDebug('CatchUp', 'Opening Update Status modal', { ticketId: selectedTicket.id, currentStatus: selectedTicket.status })
    setStatusSelection(selectedTicket.status || '')
    setActionError(null)
    setIsStatusModalOpen(true)
  }

  const handleAddResponse = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedTicket) return
    if (responseText.trim().length === 0) {
      uiWarn('CatchUp', 'Add Response submit ignored: empty response')
      return
    }

    try {
      setActionLoading('response')
      setActionError(null)
      uiDebug('CatchUp', 'Submitting Add Response', {
        ticketId: selectedTicket.id,
        internalAnalysisFlag: responseInternal,
        resolutionFlag: responseResolution,
      })

      const response = await apiFetch(`/api/tickets/${selectedTicket.id}/responses`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text: responseText.trim(),
          internalAnalysisFlag: responseInternal,
          resolutionFlag: responseResolution,
        }),
      })

      if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Failed to add response'))
      }

      await fetchTickets(false)
      uiDebug('CatchUp', 'Add Response successful', { ticketId: selectedTicket.id })
      setIsResponseModalOpen(false)
      setResponseText('')
      setResponseInternal(false)
      setResponseResolution(false)
    } catch (err: unknown) {
      if (err instanceof Error) {
        uiError('CatchUp', 'Add Response failed', { ticketId: selectedTicket.id, message: err.message })
        setActionError(err.message)
      } else {
        uiError('CatchUp', 'Add Response failed with unknown error', { ticketId: selectedTicket.id })
        setActionError('Error adding response')
      }
    } finally {
      setActionLoading(null)
    }
  }

  const handleUpdateStatus = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedTicket) return
    if (statusSelection.trim().length === 0) {
      uiWarn('CatchUp', 'Update Status submit ignored: empty status')
      return
    }

    try {
      setActionLoading('status')
      setActionError(null)
      uiDebug('CatchUp', 'Submitting Update Status', {
        ticketId: selectedTicket.id,
        fromStatus: selectedTicket.status,
        toStatus: statusSelection.trim(),
      })

      const response = await apiFetch(`/api/tickets/${selectedTicket.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: statusSelection.trim() }),
      })

      if (!response.ok) {
        throw new Error(await parseErrorMessage(response, 'Failed to update status'))
      }

      await fetchTickets(false)
      uiDebug('CatchUp', 'Update Status successful', { ticketId: selectedTicket.id, toStatus: statusSelection.trim() })
      setIsStatusModalOpen(false)
    } catch (err: unknown) {
      if (err instanceof Error) {
        uiError('CatchUp', 'Update Status failed', { ticketId: selectedTicket.id, message: err.message })
        setActionError(err.message)
      } else {
        uiError('CatchUp', 'Update Status failed with unknown error', { ticketId: selectedTicket.id })
        setActionError('Error updating status')
      }
    } finally {
      setActionLoading(null)
    }
  }

  if (loading) {
    return <CenteredLoadingBar label="Loading tickets..." />
  }

  if (loadError) {
    uiError('CatchUp', 'Rendering load error state', { message: loadError })
    return <div className="catchup-container"><p style={{ color: 'red' }}>Error: {loadError}</p></div>
  }

  return (
    <div className="catchup-container">
      {actionError && <div className="action-error-banner">Error: {actionError}</div>}
      <div className="filters-panel">
        <div className="filters-header">
          <h2>Filters</h2>
          <div className="filters-header-actions">
            <span>Use Ctrl/Cmd + click to select multiple values</span>
            <button
              type="button"
              className="btn btn-secondary btn-compact"
              onClick={() => setFiltersCollapsed((current) => {
                const nextValue = !current
                uiDebug('CatchUp', `Filters panel ${nextValue ? 'collapsed' : 'expanded'}`)
                return nextValue
              })}
            >
              {filtersCollapsed ? 'Enlarge' : 'Minimize'}
            </button>
            <button
              type="button"
              className="btn btn-secondary btn-compact"
              onClick={() => {
                uiDebug('CatchUp', 'Manual resync requested')
                fetchTickets(false)
              }}
              disabled={syncing || actionLoading !== null}
            >
              {syncing ? 'Resyncing...' : 'Resync'}
            </button>
          </div>
        </div>
        {!filtersCollapsed && (
          <div className="filters-grid">
            <div className="filter-group search-group">
              <label htmlFor="ticket-search">Search</label>
              <div className="search-input-row">
                <input
                  id="ticket-search"
                  type="text"
                  className="filter-select search-input"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="Search by ticket #, summary, board, company, owner..."
                />
                <button
                  type="button"
                  className="btn btn-secondary search-clear-btn"
                  onClick={() => setSearchQuery('')}
                  disabled={searchQuery.trim().length === 0}
                >
                  Clear
                </button>
              </div>
            </div>
            <div className="filter-group">
              <label htmlFor="filter-mode">Mode</label>
              <select
                id="filter-mode"
                className="filter-select"
                value={filterMode}
                onChange={(event) => setFilterMode(event.target.value as FilterMode)}
              >
                <option value="include">Show Only Selected</option>
                <option value="exclude">Exclude Selected</option>
              </select>
            </div>
            <div className="filter-group">
              <label htmlFor="filter-board">Board</label>
              <select
                id="filter-board"
                className="filter-select filter-multi"
                value={selectedBoards}
                onChange={(event) => setSelectedBoards(getSelectedValues(event))}
                multiple
              >
                {boardOptions.map((board) => (
                  <option key={board} value={board}>{board}</option>
                ))}
              </select>
            </div>
            <div className="filter-group">
              <label htmlFor="filter-priority">Priority</label>
              <select
                id="filter-priority"
                className="filter-select filter-multi"
                value={selectedPriorities}
                onChange={(event) => setSelectedPriorities(getSelectedValues(event))}
                multiple
              >
                {priorityOptions.map((priority) => (
                  <option key={priority} value={priority}>{priority}</option>
                ))}
              </select>
            </div>
            <div className="filter-group">
              <label htmlFor="filter-contact">Contact</label>
              <select
                id="filter-contact"
                className="filter-select filter-multi"
                value={selectedContacts}
                onChange={(event) => setSelectedContacts(getSelectedValues(event))}
                multiple
              >
                {contactOptions.map((contact) => (
                  <option key={contact} value={contact}>{contact}</option>
                ))}
              </select>
            </div>
            <div className="filter-group">
              <label htmlFor="filter-company">Company</label>
              <select
                id="filter-company"
                className="filter-select filter-multi"
                value={selectedCompanies}
                onChange={(event) => setSelectedCompanies(getSelectedValues(event))}
                multiple
              >
                {companyOptions.map((company) => (
                  <option key={company} value={company}>{company}</option>
                ))}
              </select>
            </div>
            <div className="filter-group filter-actions">
              <button type="button" className="btn btn-secondary" onClick={clearFilters}>Clear Filters</button>
            </div>
          </div>
        )}
      </div>

      <div className="catchup-layout">
        <div className="tickets-list">
          <div className="list-header">
            <div className="list-header-top">
              <h2>Open Tickets ({sortedTickets.length} of {tickets.length})</h2>
              <div className="list-sort">
                <label htmlFor="ticket-sort">Sort by</label>
                <select
                  id="ticket-sort"
                  className="filter-select"
                  value={sortOption}
                  onChange={(event) => setSortOption(event.target.value as SortOption)}
                >
                  <option value="lastUpdatedDesc">Last Updated (Newest)</option>
                  <option value="lastUpdatedAsc">Last Updated (Oldest)</option>
                  <option value="priorityHighToLow">Priority (High to Low)</option>
                  <option value="priorityLowToHigh">Priority (Low to High)</option>
                  <option value="ticketIdDesc">Ticket ID (Newest)</option>
                  <option value="ticketIdAsc">Ticket ID (Oldest)</option>
                </select>
              </div>
            </div>
          </div>
          {sortedTickets.length === 0 ? (
            <div className="empty-filter-state">
              {filteredTickets.length === 0 ? 'No tickets match the current filters.' : 'No tickets match the current search.'}
            </div>
          ) : (
            sortedTickets.map((ticket) => (
              <div
                key={ticket.id}
                className={`ticket-item ${selectedTicket?.id === ticket.id ? 'active' : ''}`}
                onClick={() => {
                  uiDebug('CatchUp', 'Selected ticket', { ticketId: ticket.id, summary: ticket.summary })
                  setSelectedTicket(ticket)
                }}
              >
                <div className="ticket-item-content">
                  <div className="ticket-header">
                    <h3>#{ticket.id} - {ticket.summary}</h3>
                    <span className={`priority-badge ${getPriorityClass(ticket.priority)}`}>
                      {ticket.priority}
                    </span>
                  </div>
                  <div className="ticket-meta">
                    <p><strong>Board:</strong> {ticket.board}</p>
                    <p><strong>Company:</strong> {ticket.company}</p>
                    <p><strong>Owner:</strong> {ticket.owner}</p>
                    <p><strong>Status:</strong> {ticket.status}</p>
                    <p className="last-update">Updated {ticket.lastUpdate}</p>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>

        <div className="ticket-details">
          {selectedTicket ? (
            <div className="details-content">
              <div className="details-header">
                <h2>#{selectedTicket.id} - {selectedTicket.summary}</h2>
                <div className="details-badges">
                  <span className="badge">{selectedTicket.status}</span>
                  <span className={`priority-badge ${getPriorityClass(selectedTicket.priority)}`}>
                    {selectedTicket.priority}
                  </span>
                </div>
              </div>

              <div className="details-info">
                <div className="info-row">
                  <span className="info-label">Board:</span>
                  <span className="info-value">{selectedTicket.board}</span>
                </div>
                <div className="info-row">
                  <span className="info-label">Company:</span>
                  <span className="info-value">{selectedTicket.company}</span>
                </div>
                <div className="info-row">
                  <span className="info-label">Owner:</span>
                  <span className="info-value">{selectedTicket.owner}</span>
                </div>
                <div className="info-row">
                  <span className="info-label">Status:</span>
                  <span className="info-value">{selectedTicket.status}</span>
                </div>
              </div>

              <div className="responses-section">
                <h3>Responses & Updates</h3>
                <div className="responses-list">
                  {selectedTicket.responses.length === 0 ? (
                    <div className="response-item">
                      <div className="response-content">No updates yet for this ticket.</div>
                    </div>
                  ) : (
                    selectedTicket.responses.map((response) => (
                      <div key={response.id} className="response-item">
                        <div className="response-header">
                          <span className="response-author">{response.author}</span>
                          <span className="response-time">{response.timestamp}</span>
                        </div>
                        <div className="response-content">
                          {renderMarkdownText(response.content)}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="action-buttons">
                <button
                  className="btn btn-primary"
                  onClick={openResponseModal}
                  disabled={syncing || actionLoading !== null}
                >
                  {actionLoading === 'response' ? 'Adding...' : 'Add Response'}
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={openStatusModal}
                  disabled={syncing || actionLoading !== null}
                >
                  {actionLoading === 'status' ? 'Updating...' : 'Update Status'}
                </button>
              </div>
            </div>
          ) : (
            <div className="no-selection">
              <p>
                {filteredTickets.length === 0
                  ? 'No tickets match the current filters'
                  : sortedTickets.length === 0
                    ? 'No tickets match the current search'
                    : 'Select a ticket to view details and responses'}
              </p>
            </div>
          )}
        </div>
      </div>

      {isResponseModalOpen && selectedTicket && (
        <div className="modal-backdrop" onClick={() => {
          uiDebug('CatchUp', 'Closing Add Response modal (backdrop click)', { ticketId: selectedTicket.id })
          setIsResponseModalOpen(false)
        }}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <h3>Add Response to #{selectedTicket.id}</h3>
            <form className="modal-form" onSubmit={handleAddResponse}>
              <label htmlFor="response-text">Response</label>
              <textarea
                id="response-text"
                className="modal-textarea"
                value={responseText}
                onChange={(event) => setResponseText(event.target.value)}
                placeholder="Type your response..."
                rows={6}
                required
              />
              <label className="modal-checkbox">
                <input
                  type="checkbox"
                  checked={responseInternal}
                  onChange={(event) => setResponseInternal(event.target.checked)}
                />
                Mark as internal analysis
              </label>
              <label className="modal-checkbox">
                <input
                  type="checkbox"
                  checked={responseResolution}
                  onChange={(event) => setResponseResolution(event.target.checked)}
                />
                Mark as resolution
              </label>
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setIsResponseModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={actionLoading !== null || syncing}>
                  {actionLoading === 'response' ? 'Adding...' : 'Add Response'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {isStatusModalOpen && selectedTicket && (
        <div className="modal-backdrop" onClick={() => {
          uiDebug('CatchUp', 'Closing Update Status modal (backdrop click)', { ticketId: selectedTicket.id })
          setIsStatusModalOpen(false)
        }}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <h3>Update Status for #{selectedTicket.id}</h3>
            <form className="modal-form" onSubmit={handleUpdateStatus}>
              <label htmlFor="status-select">Status</label>
              <select
                id="status-select"
                className="modal-select"
                value={statusSelection}
                onChange={(event) => setStatusSelection(event.target.value)}
                required
              >
                {statusOptions.map((statusOption) => (
                  <option key={statusOption} value={statusOption}>{statusOption}</option>
                ))}
              </select>
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setIsStatusModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={actionLoading !== null || syncing}>
                  {actionLoading === 'status' ? 'Updating...' : 'Update Status'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

