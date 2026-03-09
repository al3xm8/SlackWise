export type ThemeMode = 'light' | 'dark'

const THEME_STORAGE_KEY = 'slackwise.theme'

export const normalizeThemeMode = (value?: string | null): ThemeMode => (
  value === 'dark' ? 'dark' : 'light'
)

export const applyThemeMode = (theme: ThemeMode) => {
  document.documentElement.setAttribute('data-theme', theme)
}

export const readStoredThemeMode = (): ThemeMode | null => {
  const raw = window.localStorage.getItem(THEME_STORAGE_KEY)
  if (raw === 'dark' || raw === 'light') {
    return raw
  }
  return null
}

export const storeThemeMode = (theme: ThemeMode) => {
  window.localStorage.setItem(THEME_STORAGE_KEY, theme)
}

