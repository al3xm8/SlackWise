export default function Logo() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
      <svg
        width="48"
        height="48"
        viewBox="0 0 56 56"
        xmlns="http://www.w3.org/2000/svg"
        style={{ animation: 'float 3s ease-in-out infinite' }}
      >
        <defs>
          <linearGradient id="dropGradient1" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" style={{ stopColor: '#ffffff', stopOpacity: 1 }} />
            <stop offset="100%" style={{ stopColor: '#e0f2fe', stopOpacity: 1 }} />
          </linearGradient>
          <linearGradient id="dropGradient2" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" style={{ stopColor: '#ffffff', stopOpacity: 0.6 }} />
            <stop offset="100%" style={{ stopColor: '#e0f2fe', stopOpacity: 0.6 }} />
          </linearGradient>
          <filter id="dropShadow">
            <feDropShadow dx="0" dy="4" stdDeviation="3" floodOpacity="0.4" floodColor="#ffffff" />
          </filter>
        </defs>
        <path
          d="M28 4 C28 4, 8 20, 8 32 C8 43.046, 16.954 52, 28 52 C39.046 52, 48 43.046, 48 32 C48 20, 28 4, 28 4 Z"
          fill="url(#dropGradient1)"
          filter="url(#dropShadow)"
        />
        <ellipse cx="24" cy="24" rx="8" ry="12" fill="url(#dropGradient2)" />
        <path
          d="M28 18 L28 34 M28 34 L22 28 M28 34 L34 28"
          stroke="#3b82f6"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
          opacity="0.95"
        />
      </svg>
      <style>{`
        @keyframes float {
          0%, 100% { transform: translateY(0px); }
          50% { transform: translateY(-8px); }
        }
      `}</style>
    </div>
  )
}
