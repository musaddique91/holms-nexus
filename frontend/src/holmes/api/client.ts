import type {
  Dashboard,
  Incident,
  IncidentDetail,
  IncidentStatus,
  Investigation,
  LlmSettings,
  LlmSettingsUpdate,
  LlmTestResult,
  Channel,
  NotificationSettings,
  NotificationSettingsUpdate,
  NotificationTestResult,
  Page,
  Service,
  ServiceInput,
  Toolset,
} from './types'
import { auth, sessionExpired } from '@/lib/auth'

export { auth }

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = auth.token()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (res.status === 401 && path !== '/api/auth/login') {
    sessionExpired()
    throw new ApiError(401, 'Session expired')
  }
  if (!res.ok) {
    let detail = res.statusText
    try {
      const problem = await res.json()
      detail = problem.detail ?? problem.message ?? detail
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, detail)
  }
  if (res.status === 204) return undefined as T
  try {
    return (await res.json()) as T
  } catch {
    throw new ApiError(res.status, 'Unexpected response from the server')
  }
}

export interface IncidentFilter {
  status?: IncidentStatus | ''
  service?: string
  q?: string
  page?: number
}

const qs = (params: Record<string, string | number | undefined>) => {
  const s = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') s.set(k, String(v))
  })
  const str = s.toString()
  return str ? `?${str}` : ''
}

export const api = {
  login: (username: string, password: string) =>
    request<{ token: string; expiresAt: string; username: string }>('POST', '/api/auth/login', {
      username,
      password,
    }),
  me: () => request<{ username: string }>('GET', '/api/auth/me'),

  dashboard: () => request<Dashboard>('GET', '/api/dashboard'),

  incidents: (f: IncidentFilter) =>
    request<Page<Incident>>(
      'GET',
      `/api/incidents${qs({ status: f.status, service: f.service, q: f.q, page: f.page, size: 25 })}`,
    ),
  incident: (id: number) => request<IncidentDetail>('GET', `/api/incidents/${id}`),
  incidentAction: (id: number, action: 'reinvestigate' | 'fix' | 'resolve' | 'ignore') =>
    request<{ status: string }>('POST', `/api/incidents/${id}/${action}`),

  services: () => request<Service[]>('GET', '/api/services'),
  createService: (s: ServiceInput) => request<Service>('POST', '/api/services', s),
  updateService: (id: number, s: ServiceInput) => request<Service>('PUT', `/api/services/${id}`, s),
  removeService: (id: number) => request<void>('DELETE', `/api/services/${id}`),

  toolsets: (probe: boolean) => request<Toolset[]>('GET', `/api/toolsets?probe=${probe}`),

  llmSettings: () => request<LlmSettings>('GET', '/api/settings/llm'),
  saveLlmSettings: (u: LlmSettingsUpdate) => request<LlmSettings>('PUT', '/api/settings/llm', u),
  llmModels: (u: LlmSettingsUpdate) => request<string[]>('POST', '/api/settings/llm/models', u),
  testLlm: (u: LlmSettingsUpdate) => request<LlmTestResult>('POST', '/api/settings/llm/test', u),

  notificationSettings: () => request<NotificationSettings>('GET', '/api/settings/notifications'),
  saveNotificationSettings: (u: NotificationSettingsUpdate) =>
    request<NotificationSettings>('PUT', '/api/settings/notifications', u),
  testNotification: (channel: Channel, u: NotificationSettingsUpdate) =>
    request<NotificationTestResult>('POST', `/api/settings/notifications/test/${channel}`, u),

  ask: (question: string) => request<{ id: number }>('POST', '/api/chat', { question }),
  chatHistory: () => request<Investigation[]>('GET', '/api/chat'),
  investigation: (id: number) => request<Investigation>('GET', `/api/investigations/${id}`),
}
