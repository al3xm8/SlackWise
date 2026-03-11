import { Link, NavLink } from 'react-router-dom'
import BrandLogo from './BrandLogo'
import '../styles/Navbar.css'

interface NavbarProps {
  onSignOut: () => void
}

export default function Navbar({ onSignOut }: NavbarProps) {
  return (
    <nav className="navbar">
      <div className="navbar-container">
        <Link to="/app/dashboard" className="navbar-brand">
          <BrandLogo alt="Dropwise" className="navbar-brand-logo" />
        </Link>
        <ul className="navbar-menu">
          <li>
            <NavLink to="/app/dashboard" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Dashboard
            </NavLink>
          </li>
          <li>
            <NavLink to="/app/catchup" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Catch Up
            </NavLink>
          </li>
          <li>
            <NavLink to="/app/rules" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Rules
            </NavLink>
          </li>
          <li>
            <NavLink to="/app/settings" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              Settings
            </NavLink>
          </li>
          <li>
            <button type="button" className="nav-link nav-link-button" onClick={onSignOut}>
              Sign out
            </button>
          </li>
        </ul>
      </div>
    </nav>
  )
}
