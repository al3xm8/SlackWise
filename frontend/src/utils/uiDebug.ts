const getTimestamp = (): string => {
  const now = new Date()
  return now.toISOString().replace(/\.\d{3}Z$/, 'Z')
}

const isDebugEnabled = (): boolean => {
  if (import.meta.env.DEV) return true
  if (typeof window === 'undefined') return false
  return window.localStorage.getItem('debug:ui') === '1'
}

export const uiDebug = (scope: string, message: string, meta?: unknown): void => {
  if (!isDebugEnabled()) return

  const prefix = `<${getTimestamp()}> [UI:${scope}]`
  if (meta === undefined) {
    console.log(prefix, message)
    return
  }
  console.log(prefix, message, meta)
}

export const uiWarn = (scope: string, message: string, meta?: unknown): void => {
  if (!isDebugEnabled()) return

  const prefix = `<${getTimestamp()}> [UI:${scope}]`
  if (meta === undefined) {
    console.warn(prefix, message)
    return
  }
  console.warn(prefix, message, meta)
}

export const uiError = (scope: string, message: string, meta?: unknown): void => {
  if (!isDebugEnabled()) return

  const prefix = `<${getTimestamp()}> [UI:${scope}]`
  if (meta === undefined) {
    console.error(prefix, message)
    return
  }
  console.error(prefix, message, meta)
}
