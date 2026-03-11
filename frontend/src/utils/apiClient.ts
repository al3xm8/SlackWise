import { readAuthSession } from './session'

export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const session = readAuthSession()
  const headers = new Headers(init.headers ?? undefined)

  if (session.accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  return fetch(input, {
    ...init,
    headers,
  })
}
