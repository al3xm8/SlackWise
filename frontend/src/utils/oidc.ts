export type OidcIntent = 'signin' | 'signup'

interface OidcConfig {
  issuer: string
  clientId: string
  audience: string
  scope: string
  redirectUri: string
  tenantClaimNames: string[]
}

interface OidcTransaction {
  state: string
  codeVerifier: string
  redirectUri: string
  createdAt: number
}

interface OidcDiscovery {
  authorization_endpoint: string
  token_endpoint: string
}

export interface OidcResolvedSession {
  email: string
  name: string
  accessToken: string
  idToken: string
  tenantId: string
}

const OIDC_TRANSACTION_KEY = 'slackwise.oidc.transaction'
const DEFAULT_SCOPE = 'openid profile email'
const DEFAULT_TENANT_CLAIMS = ['tenant_id', 'tenantId', 'company_id', 'org_id']

export function hasOidcConfig(): boolean {
  return resolveOidcConfig() !== null
}

export async function startOidcFlow(intent: OidcIntent): Promise<void> {
  const config = requireOidcConfig()
  const discovery = await fetchOidcDiscovery(config.issuer)

  const state = randomBase64Url(32)
  const codeVerifier = randomBase64Url(48)
  const codeChallenge = await pkceChallengeFromVerifier(codeVerifier)

  const transaction: OidcTransaction = {
    state,
    codeVerifier,
    redirectUri: config.redirectUri,
    createdAt: Date.now(),
  }

  window.sessionStorage.setItem(OIDC_TRANSACTION_KEY, JSON.stringify(transaction))

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    scope: config.scope,
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
  })

  if (config.audience) {
    params.set('audience', config.audience)
  }

  if (intent === 'signup') {
    params.set('screen_hint', 'signup')
  }

  window.location.assign(`${discovery.authorization_endpoint}?${params.toString()}`)
}

export async function completeOidcFlow(callbackUrl: string): Promise<OidcResolvedSession> {
  const config = requireOidcConfig()
  const discovery = await fetchOidcDiscovery(config.issuer)

  const url = new URL(callbackUrl)
  const error = url.searchParams.get('error')
  const errorDescription = url.searchParams.get('error_description')
  if (error) {
    clearOidcTransaction()
    throw new Error(errorDescription || `OIDC error: ${error}`)
  }

  const code = url.searchParams.get('code')
  const state = url.searchParams.get('state')
  if (!code || !state) {
    clearOidcTransaction()
    throw new Error('Missing code or state in OIDC callback URL')
  }

  const tx = readOidcTransaction()
  if (!tx) {
    throw new Error('Missing OIDC transaction in session storage')
  }

  if (state !== tx.state) {
    clearOidcTransaction()
    throw new Error('OIDC state mismatch')
  }

  const tokenResponse = await fetch(discovery.token_endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      redirect_uri: tx.redirectUri,
      code_verifier: tx.codeVerifier,
    }),
  })

  const tokenPayload = await tokenResponse.json() as {
    access_token?: string
    id_token?: string
    error?: string
    error_description?: string
  }

  if (!tokenResponse.ok || !tokenPayload.access_token) {
    clearOidcTransaction()
    const message = tokenPayload.error_description || tokenPayload.error || 'OIDC token exchange failed'
    throw new Error(message)
  }

  clearOidcTransaction()

  const idToken = tokenPayload.id_token ?? ''
  const idClaims = decodeJwtClaims(idToken)
  const accessClaims = decodeJwtClaims(tokenPayload.access_token)

  const email = pickFirstString([
    idClaims.email,
    idClaims.preferred_username,
    accessClaims.email,
  ])

  const name = pickFirstString([
    idClaims.name,
    idClaims.nickname,
    accessClaims.name,
    email ? email.split('@')[0] : '',
  ])

  const tenantId = extractTenantId(idClaims, accessClaims, config.tenantClaimNames)

  return {
    email,
    name,
    accessToken: tokenPayload.access_token,
    idToken,
    tenantId,
  }
}

function resolveOidcConfig(): OidcConfig | null {
  const issuer = (import.meta.env.VITE_OIDC_ISSUER ?? '').trim().replace(/\/$/, '')
  const clientId = (import.meta.env.VITE_OIDC_CLIENT_ID ?? '').trim()
  if (!issuer || !clientId) {
    return null
  }

  const redirectPath = (import.meta.env.VITE_OIDC_REDIRECT_PATH ?? '/auth/callback').trim()
  const redirectUri = `${window.location.origin}${redirectPath.startsWith('/') ? redirectPath : `/${redirectPath}`}`

  const scope = (import.meta.env.VITE_OIDC_SCOPE ?? DEFAULT_SCOPE).trim() || DEFAULT_SCOPE
  const audience = (import.meta.env.VITE_OIDC_AUDIENCE ?? '').trim()
  const tenantClaimsRaw = (import.meta.env.VITE_OIDC_TENANT_CLAIMS ?? '').trim()
  const tenantClaimNames = tenantClaimsRaw.length > 0
    ? tenantClaimsRaw.split(',').map((value: string) => value.trim()).filter((value: string) => value.length > 0)
    : DEFAULT_TENANT_CLAIMS

  return {
    issuer,
    clientId,
    audience,
    scope,
    redirectUri,
    tenantClaimNames,
  }
}

function requireOidcConfig(): OidcConfig {
  const config = resolveOidcConfig()
  if (!config) {
    throw new Error('OIDC is not configured. Set VITE_OIDC_ISSUER and VITE_OIDC_CLIENT_ID.')
  }
  return config
}

async function fetchOidcDiscovery(issuer: string): Promise<OidcDiscovery> {
  const response = await fetch(`${issuer}/.well-known/openid-configuration`)
  if (!response.ok) {
    throw new Error('Failed to load OIDC discovery metadata')
  }

  const data = await response.json() as Partial<OidcDiscovery>
  if (!data.authorization_endpoint || !data.token_endpoint) {
    throw new Error('OIDC discovery metadata is missing required endpoints')
  }

  return {
    authorization_endpoint: data.authorization_endpoint,
    token_endpoint: data.token_endpoint,
  }
}

function readOidcTransaction(): OidcTransaction | null {
  const raw = window.sessionStorage.getItem(OIDC_TRANSACTION_KEY)
  if (!raw) {
    return null
  }

  try {
    const parsed = JSON.parse(raw) as Partial<OidcTransaction>
    if (!parsed.state || !parsed.codeVerifier || !parsed.redirectUri) {
      return null
    }

    return {
      state: parsed.state,
      codeVerifier: parsed.codeVerifier,
      redirectUri: parsed.redirectUri,
      createdAt: typeof parsed.createdAt === 'number' ? parsed.createdAt : Date.now(),
    }
  } catch {
    return null
  }
}

function clearOidcTransaction(): void {
  window.sessionStorage.removeItem(OIDC_TRANSACTION_KEY)
}

function randomBase64Url(byteLength: number): string {
  const bytes = new Uint8Array(byteLength)
  window.crypto.getRandomValues(bytes)
  return base64UrlEncode(bytes)
}

async function pkceChallengeFromVerifier(verifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const digest = await window.crypto.subtle.digest('SHA-256', encoder.encode(verifier))
  return base64UrlEncode(new Uint8Array(digest))
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = ''
  bytes.forEach((value) => {
    binary += String.fromCharCode(value)
  })

  return window.btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function decodeJwtClaims(token: string): Record<string, unknown> {
  if (!token || token.split('.').length < 2) {
    return {}
  }

  try {
    const payload = token.split('.')[1] ?? ''
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const json = window.atob(padded)
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}

function pickFirstString(values: Array<unknown>): string {
  for (const value of values) {
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim()
    }
  }
  return ''
}

function extractTenantId(
  idClaims: Record<string, unknown>,
  accessClaims: Record<string, unknown>,
  claimNames: string[],
): string {
  for (const claimName of claimNames) {
    const idValue = idClaims[claimName]
    if (typeof idValue === 'string' && idValue.trim().length > 0) {
      return idValue.trim()
    }

    const accessValue = accessClaims[claimName]
    if (typeof accessValue === 'string' && accessValue.trim().length > 0) {
      return accessValue.trim()
    }
  }

  return ''
}


