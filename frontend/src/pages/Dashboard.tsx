import '../styles/Dashboard.css'

export default function Dashboard() {
  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>Dashboard</h2>
        <p>Welcome to Wisedrop</p>
      </div>
      
      <div className="dashboard-empty">
        <div className="empty-content">
          <p>Your dashboard is ready. Start by configuring your routing rules.</p>
        </div>
      </div>
    </div>
  )
}
