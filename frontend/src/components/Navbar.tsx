import { Link, useLocation } from 'react-router-dom'
import Logo from './Logo'
import '../styles/Navbar.css'

export default function Navbar() {
  const location = useLocation()

  const isActive = (path: string) => location.pathname === path

  return (
    <nav className="navbar">
      <div className="navbar-container">
        <div className="navbar-brand">
          <Logo />
          <h1><span style={{ color: 'black' }}>wise</span>drop</h1>
        </div>
        <ul className="navbar-menu">
          <li>
            <Link 
              to="/" 
              className={`nav-link ${isActive('/') ? 'active' : ''}`}
            >
              Dashboard
            </Link>
          </li>
          <li>
            <Link 
              to="/rules" 
              className={`nav-link ${isActive('/rules') ? 'active' : ''}`}
            >
              Rules
            </Link>
          </li>
          <li>
            <Link 
              to="/settings" 
              className={`nav-link ${isActive('/settings') ? 'active' : ''}`}
            >
              Settings
            </Link>
          </li>
        </ul>
      </div>
    </nav>
  )
}
