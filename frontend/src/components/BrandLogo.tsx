import { useEffect, useState } from 'react'

type ThemeMode = 'light' | 'dark'

interface BrandLogoProps {
  className?: string
  alt?: string
}

function readThemeMode(): ThemeMode {
  if (typeof document === 'undefined') {
    return 'light'
  }

  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

export default function BrandLogo({ className, alt = 'Dropwise' }: BrandLogoProps) {
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => readThemeMode())

  useEffect(() => {
    const root = document.documentElement
    const observer = new MutationObserver(() => {
      setThemeMode(readThemeMode())
    })

    observer.observe(root, { attributes: true, attributeFilter: ['data-theme'] })
    return () => {
      observer.disconnect()
    }
  }, [])

  const src = themeMode === 'dark'
    ? '/dropwise-logo-horizontal-dark.svg'
    : '/dropwise-logo-horizontal-light.svg'

  return <img src={src} alt={alt} className={className} />
}
