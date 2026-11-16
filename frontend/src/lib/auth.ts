/**
 * One sign-in for both apps (Intelligence and MCP): the JWT from POST /api/auth/login, kept in
 * localStorage and sent as a Bearer token on every API call, including the MCP chat streams.
 */
const TOKEN_KEY = 'nexus.token'

export const auth = {
  token(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* private mode: the token lives for this page only */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
  /** Headers for a fetch: the Bearer token when signed in. */
  headers(extra: Record<string, string> = {}): Record<string, string> {
    const t = auth.token()
    return t ? { ...extra, Authorization: `Bearer ${t}` } : extra
  },
}

let redirecting = false

/** The session ended (401): forget the token and go to the login page once. */
export function sessionExpired() {
  auth.clear()
  if (!redirecting && !window.location.pathname.startsWith('/login')) {
    redirecting = true
    const next = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.assign(`/login?next=${next}`)
  }
}
