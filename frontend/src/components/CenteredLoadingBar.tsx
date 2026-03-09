interface CenteredLoadingBarProps {
  label?: string
}

export default function CenteredLoadingBar({ label = 'Loading...' }: CenteredLoadingBarProps) {
  return (
    <div className="loading-screen" role="status" aria-live="polite" aria-busy="true">
      <div className="loading-scene-wrap">
        <div className="loading-drop-scene" aria-hidden="true">
          <div className="loading-drop">
            <svg viewBox="0 0 120 150" className="loading-drop-svg">
              <defs>
                <linearGradient id="dropGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#4ea8ff" />
                  <stop offset="100%" stopColor="#18b6d9" />
                </linearGradient>
              </defs>
              <path
                d="M60 6C60 6 14 55 14 91C14 122 34 144 60 144C86 144 106 122 106 91C106 55 60 6 60 6Z"
                fill="url(#dropGradient)"
              />
              <path
                d="M60 52V93M60 93L45 78M60 93L75 78"
                fill="none"
                stroke="#eff7ff"
                strokeWidth="9"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </div>

          <div className="loading-bucket">
            <div className="loading-water" />
            <div className="loading-splash loading-splash-left" />
            <div className="loading-splash loading-splash-right" />
            <div className="loading-ripple" />
          </div>
        </div>

        <p className="loading-bar-label">{label}</p>
      </div>
    </div>
  )
}
