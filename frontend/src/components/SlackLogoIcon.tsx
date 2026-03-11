interface SlackLogoIconProps {
  className?: string
}

export default function SlackLogoIcon({ className }: SlackLogoIconProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 72 72"
      role="img"
      aria-hidden="true"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="4" y="27" width="31" height="18" rx="9" fill="#36C5F0" />
      <rect x="27" y="4" width="18" height="31" rx="9" fill="#36C5F0" />

      <rect x="27" y="37" width="18" height="31" rx="9" fill="#E01E5A" />
      <rect x="4" y="37" width="31" height="18" rx="9" fill="#E01E5A" />

      <rect x="37" y="4" width="18" height="31" rx="9" fill="#2EB67D" />
      <rect x="37" y="27" width="31" height="18" rx="9" fill="#2EB67D" />

      <rect x="37" y="37" width="31" height="18" rx="9" fill="#ECB22E" />
      <rect x="37" y="37" width="18" height="31" rx="9" fill="#ECB22E" />
    </svg>
  )
}
