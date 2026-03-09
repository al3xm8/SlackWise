import { Link, NavLink } from 'react-router-dom'
import '../styles/Navbar.css'

export default function Navbar() {
  return (
    <nav className="navbar">
      <div className="navbar-container">
        <Link to="/" className="navbar-brand">
          <img
            src="/wisedrop-logo-horizontal.svg"
            alt="Wisedrop"
            className="navbar-brand-logo"
          />
        </Link>
        <ul className="navbar-menu">
          <li>
            <NavLink to="/dashboard" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Dashboard
            </NavLink>
          </li>
          <li>
            <NavLink to="/catchup" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Catch Up
            </NavLink>
          </li>
          <li>
            <NavLink to="/rules" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Rules
            </NavLink>
          </li>
          <li>
            <NavLink to="/settings" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Settings
            </NavLink>
          </li>
        </ul>
      </div>
    </nav>
  )
}
