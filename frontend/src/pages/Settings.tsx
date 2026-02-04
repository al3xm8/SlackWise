import { useState, useEffect } from 'react'
import '../styles/Settings.css'

const translations = {
  en: {
    settingsTitle: 'Settings',
    settingsSubtitle: 'Manage your application preferences and integrations',
    savedMessage: 'Settings saved successfully!',
    appearance: 'Appearance',
    theme: 'Theme',
    light: 'Light',
    dark: 'Dark',
    language: 'Language',
    apiKeys: 'API Keys & Integrations',
    connectwisePrivate: 'ConnectWise Private Key',
    connectwiseSecret: 'ConnectWise Secret Key',
    connectwisePrivatePlaceholder: 'Enter your ConnectWise private key',
    connectwiseSecretPlaceholder: 'Enter your ConnectWise secret key',
    connectwiseHelp: 'Required to authenticate with ConnectWise API',
    slackIntegration: 'Slack Integration',
    slackConnected: 'Connected to Slack',
    slackConnect: 'Connect to Slack',
    slackDisconnect: 'Disconnect',
    slackHelp: 'Connect Wisedrop to your Slack workspace to route tickets',
    saveSettings: 'Save Settings',
    connectwiseAlert: 'Please fill in ConnectWise credentials',
  },
  es: {
    settingsTitle: 'Configuración',
    settingsSubtitle: 'Gestiona las preferencias e integraciones de la aplicación',
    savedMessage: '¡Configuración guardada correctamente!',
    appearance: 'Apariencia',
    theme: 'Tema',
    light: 'Claro',
    dark: 'Oscuro',
    language: 'Idioma',
    apiKeys: 'Claves API e integraciones',
    connectwisePrivate: 'Clave privada de ConnectWise',
    connectwiseSecret: 'Clave secreta de ConnectWise',
    connectwisePrivatePlaceholder: 'Ingresa tu clave privada de ConnectWise',
    connectwiseSecretPlaceholder: 'Ingresa tu clave secreta de ConnectWise',
    connectwiseHelp: 'Necesario para autenticar con la API de ConnectWise',
    slackIntegration: 'Integración con Slack',
    slackConnected: 'Conectado a Slack',
    slackConnect: 'Conectar con Slack',
    slackDisconnect: 'Desconectar',
    slackHelp: 'Conecta Wisedrop a tu espacio de trabajo de Slack para enrutar tickets',
    saveSettings: 'Guardar configuración',
    connectwiseAlert: 'Completa las credenciales de ConnectWise',
  },
  fr: {
    settingsTitle: 'Paramètres',
    settingsSubtitle: 'Gérez les préférences et intégrations de l’application',
    savedMessage: 'Paramètres enregistrés avec succès !',
    appearance: 'Apparence',
    theme: 'Thème',
    light: 'Clair',
    dark: 'Sombre',
    language: 'Langue',
    apiKeys: 'Clés API et intégrations',
    connectwisePrivate: 'Clé privée ConnectWise',
    connectwiseSecret: 'Clé secrète ConnectWise',
    connectwisePrivatePlaceholder: 'Saisissez votre clé privée ConnectWise',
    connectwiseSecretPlaceholder: 'Saisissez votre clé secrète ConnectWise',
    connectwiseHelp: 'Nécessaire pour s’authentifier auprès de l’API ConnectWise',
    slackIntegration: 'Intégration Slack',
    slackConnected: 'Connecté à Slack',
    slackConnect: 'Se connecter à Slack',
    slackDisconnect: 'Déconnecter',
    slackHelp: 'Connectez Wisedrop à votre espace de travail Slack pour acheminer les tickets',
    saveSettings: 'Enregistrer',
    connectwiseAlert: 'Veuillez renseigner les identifiants ConnectWise',
  },
  de: {
    settingsTitle: 'Einstellungen',
    settingsSubtitle: 'Verwalten Sie Ihre Anwendungseinstellungen und Integrationen',
    savedMessage: 'Einstellungen erfolgreich gespeichert!',
    appearance: 'Erscheinungsbild',
    theme: 'Design',
    light: 'Hell',
    dark: 'Dunkel',
    language: 'Sprache',
    apiKeys: 'API-Schlüssel & Integrationen',
    connectwisePrivate: 'ConnectWise-Privatschlüssel',
    connectwiseSecret: 'ConnectWise-Geheimschlüssel',
    connectwisePrivatePlaceholder: 'Geben Sie Ihren ConnectWise-Privatschlüssel ein',
    connectwiseSecretPlaceholder: 'Geben Sie Ihren ConnectWise-Geheimschlüssel ein',
    connectwiseHelp: 'Erforderlich zur Authentifizierung bei der ConnectWise-API',
    slackIntegration: 'Slack-Integration',
    slackConnected: 'Mit Slack verbunden',
    slackConnect: 'Mit Slack verbinden',
    slackDisconnect: 'Trennen',
    slackHelp: 'Verbinden Sie Wisedrop mit Ihrem Slack-Workspace, um Tickets weiterzuleiten',
    saveSettings: 'Einstellungen speichern',
    connectwiseAlert: 'Bitte geben Sie die ConnectWise-Anmeldedaten ein',
  },
} as const

type LanguageKey = keyof typeof translations

const languageOptions: { value: LanguageKey; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Spanish' },
  { value: 'fr', label: 'French' },
  { value: 'de', label: 'German' },
]

const isValidLanguage = (value: string): value is LanguageKey =>
  Object.prototype.hasOwnProperty.call(translations, value)

export default function Settings() {
  // Theme state - persisted separately
  const [theme, setTheme] = useState<'light' | 'dark'>('dark')

  // API Keys state - loaded from localStorage
  const [apiKeys, setApiKeys] = useState({
    connectwisePrivate: '',
    connectwiseSecret: '',
  })

  // Slack auth state - persisted separately
  const [slackAuth, setSlackAuth] = useState({
    connected: false,
    workspace: ''
  })

  // Language state
  const [language, setLanguage] = useState<LanguageKey>('en')
  const [saved, setSaved] = useState(false)

  const t = translations[language]

  // Load settings from localStorage on mount
  useEffect(() => {
    try {
      console.log('Loading settings from localStorage...')
      
      const savedTheme = localStorage.getItem('wisedrop-theme')
      if (savedTheme && (savedTheme === 'light' || savedTheme === 'dark')) {
        setTheme(savedTheme as 'light' | 'dark')
        document.documentElement.setAttribute('data-theme', savedTheme)
      } else {
        document.documentElement.setAttribute('data-theme', 'dark')
      }

      const savedApiKeys = localStorage.getItem('wisedrop-api-keys')
      if (savedApiKeys) {
        try {
          const parsed = JSON.parse(savedApiKeys)
          setApiKeys(parsed)
        } catch (parseError) {
          console.warn('Invalid API keys in localStorage:', parseError)
        }
      }

      const savedLanguage = localStorage.getItem('wisedrop-language')
      if (savedLanguage && isValidLanguage(savedLanguage)) {
        setLanguage(savedLanguage)
        document.documentElement.lang = savedLanguage
      } else {
        document.documentElement.lang = 'en'
      }

      const savedAuth = localStorage.getItem('wisedrop-slack-auth')
      if (savedAuth) {
        try {
          const parsed = JSON.parse(savedAuth)
          setSlackAuth(parsed)
        } catch (parseError) {
          console.warn('Invalid Slack auth data in localStorage:', parseError)
        }
      }
      
      console.log('Settings loaded successfully')
    } catch (error) {
      console.error('Error loading settings:', error)
    }
  }, [])

  const handleThemeChange = (newTheme: 'light' | 'dark') => {
    setTheme(newTheme)
    localStorage.setItem('wisedrop-theme', newTheme)
    document.documentElement.setAttribute('data-theme', newTheme)
  }

  const handleApiKeyChange = (key: keyof typeof apiKeys, value: string) => {
    setApiKeys(prev => {
      const updated = { ...prev, [key]: value }
      localStorage.setItem('wisedrop-api-keys', JSON.stringify(updated))
      return updated
    })
  }

  const handleLanguageChange = (newLanguage: string) => {
    if (!isValidLanguage(newLanguage)) return
    setLanguage(newLanguage)
    localStorage.setItem('wisedrop-language', newLanguage)
    document.documentElement.lang = newLanguage
  }

  const handleSlackAuth = () => {
    const newAuth = {
      connected: true,
      workspace: 'Think Social Workspace'
    }
    setSlackAuth(newAuth)
    localStorage.setItem('wisedrop-slack-auth', JSON.stringify(newAuth))
  }

  const handleDisconnectSlack = () => {
    const newAuth = {
      connected: false,
      workspace: ''
    }
    setSlackAuth(newAuth)
    localStorage.setItem('wisedrop-slack-auth', JSON.stringify(newAuth))
  }

  const handleSaveSettings = () => {
    if (!apiKeys.connectwisePrivate || !apiKeys.connectwiseSecret) {
      alert(t.connectwiseAlert)
      return
    }

    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  const maskApiKey = (key: string) => {
    if (!key) return ''
    if (key.length <= 8) {
      return '*'.repeat(key.length)
    }
    return key.slice(0, 4) + '*'.repeat(key.length - 8) + key.slice(-4)
  }

  return (
    <div className="settings">
      <div className="settings-header">
        <h2>{t.settingsTitle}</h2>
        <p>{t.settingsSubtitle}</p>
      </div>

      {saved && <div className="success-message">{t.savedMessage}</div>}

      <div className="settings-container">
        {/* Appearance Section */}
        <section className="settings-section">
          <h3>{t.appearance}</h3>
          <div className="section-content">
            <div className="setting-item">
              <label>{t.theme}</label>
              <div className="theme-options">
                <button
                  className={`theme-btn ${theme === 'light' ? 'active' : ''}`}
                  onClick={() => handleThemeChange('light')}
                >
                  <span className="theme-icon">☀️</span>
                  {t.light}
                </button>
                <button
                  className={`theme-btn ${theme === 'dark' ? 'active' : ''}`}
                  onClick={() => handleThemeChange('dark')}
                >
                  <span className="theme-icon">🌙</span>
                  {t.dark}
                </button>
              </div>
            </div>

            <div className="setting-item">
              <label>{t.language}</label>
              <select
                value={language}
                onChange={(e) => handleLanguageChange(e.target.value)}
                className="settings-select"
              >
                {languageOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </section>

        {/* API Keys & Integrations Section */}
        <section className="settings-section">
          <h3>{t.apiKeys}</h3>
          <div className="section-content">
            <div className="setting-item">
              <label>{t.connectwisePrivate}</label>
              <div className="api-key-input-wrapper">
                <input
                  type="password"
                  placeholder={t.connectwisePrivatePlaceholder}
                  value={apiKeys.connectwisePrivate}
                  onChange={(e) => handleApiKeyChange('connectwisePrivate', e.target.value)}
                  className="api-key-input"
                />
                {apiKeys.connectwisePrivate && (
                  <span className="api-key-masked">{maskApiKey(apiKeys.connectwisePrivate)}</span>
                )}
              </div>
              <p className="help-text">{t.connectwiseHelp}</p>
            </div>

            <div className="setting-item">
              <label>{t.connectwiseSecret}</label>
              <div className="api-key-input-wrapper">
                <input
                  type="password"
                  placeholder={t.connectwiseSecretPlaceholder}
                  value={apiKeys.connectwiseSecret}
                  onChange={(e) => handleApiKeyChange('connectwiseSecret', e.target.value)}
                  className="api-key-input"
                />
                {apiKeys.connectwiseSecret && (
                  <span className="api-key-masked">{maskApiKey(apiKeys.connectwiseSecret)}</span>
                )}
              </div>
              <p className="help-text">{t.connectwiseHelp}</p>
            </div>

            <div className="setting-item">
              <label>{t.slackIntegration}</label>
              <div className="slack-auth-wrapper">
                {slackAuth.connected ? (
                  <div className="slack-connected">
                    <div className="connection-status">
                      <span className="status-indicator connected"></span>
                      <div className="status-info">
                        <p className="status-label">{t.slackConnected}</p>
                        <p className="workspace-name">{slackAuth.workspace}</p>
                      </div>
                    </div>
                    <button 
                      className="disconnect-btn"
                      onClick={handleDisconnectSlack}
                    >
                      {t.slackDisconnect}
                    </button>
                  </div>
                ) : (
                  <button 
                    className="slack-auth-btn"
                    onClick={handleSlackAuth}
                  >
                    <span className="slack-icon">⚡</span>
                    {t.slackConnect}
                  </button>
                )}
              </div>
              <p className="help-text">{t.slackHelp}</p>
            </div>
          </div>
        </section>

        {/* Save Button */}
        <div className="settings-footer">
          <button className="btn-primary" onClick={handleSaveSettings}>
            {t.saveSettings}
          </button>
        </div>
      </div>
    </div>
  )
}
