import { auth, sessionExpired } from './auth'
import type {
  AppSettings, CatalogEntry, ConversationDetail, ConversationSummary, LogLine, McpServer, Prompt, Provider,
  ProviderRequest, RegistryServer, Resource, ServerRequest, Tool, Toolset, Workspace,
} from './types'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const res = await fetch(`/api${url}`, {
    method,
    headers: auth.headers(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 401) {
    sessionExpired()
    throw new ApiError(401, 'Session expired')
  }
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const data = await res.json()
      message = data.detail || data.message || message
    } catch { /* not json */ }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

const get = <T,>(u: string) => request<T>('GET', u)
const post = <T,>(u: string, b?: unknown) => request<T>('POST', u, b ?? {})
const put = <T,>(u: string, b: unknown) => request<T>('PUT', u, b)
const patch = <T,>(u: string, b: unknown) => request<T>('PATCH', u, b)
const del = (u: string) => request<void>('DELETE', u)

export const api = {
  servers: {
    list: () => get<McpServer[]>('/mcp/servers'),
    create: (r: ServerRequest) => post<McpServer>('/mcp/servers', r),
    update: (id: string, r: ServerRequest) => put<McpServer>(`/mcp/servers/${id}`, r),
    setEnabled: (id: string, enabled: boolean) => patch<McpServer>(`/mcp/servers/${id}/enabled`, { enabled }),
    remove: (id: string) => del(`/mcp/servers/${id}`),
    reconnect: (id: string) => post<McpServer>(`/mcp/servers/${id}/reconnect`),
    test: (r: ServerRequest) => post<McpServer>('/mcp/servers/test', r),
    tools: (id: string) => get<Tool[]>(`/mcp/servers/${id}/tools`),
    callTool: (id: string, tool: string, args: Record<string, unknown>) =>
      post<{ error: boolean; text: string; structured: unknown; durationMs: number }>(`/mcp/servers/${id}/tools/call`, { tool, arguments: args }),
    resources: (id: string) => get<Resource[]>(`/mcp/servers/${id}/resources`),
    readResource: (id: string, uri: string) =>
      post<{ uri: string; mimeType?: string; text: string; binary: boolean }[]>(`/mcp/servers/${id}/resources/read`, { uri }),
    prompts: (id: string) => get<Prompt[]>(`/mcp/servers/${id}/prompts`),
    getPrompt: (id: string, name: string, args: Record<string, unknown>) =>
      post<{ role: string; text: string }[]>(`/mcp/servers/${id}/prompts/get`, { name, arguments: args }),
    logs: (id: string) => get<LogLine[]>(`/mcp/servers/${id}/logs`),
  },
  catalog: {
    list: () => get<CatalogEntry[]>('/mcp/catalog'),
    install: (catalogId: string, values: Record<string, string>, name?: string) =>
      post<McpServer>('/mcp/catalog/install', { catalogId, values, name }),
  },
  registry: {
    search: (q: string, cursor?: string | null) => {
      const p = new URLSearchParams()
      if (q) p.set('q', q)
      if (cursor) p.set('cursor', cursor)
      return get<{ servers: RegistryServer[]; nextCursor?: string | null }>(`/mcp/registry?${p}`)
    },
    install: (name: string, optionId: string, values: Record<string, string>, displayName?: string) =>
      post<McpServer>('/mcp/registry/install', { name, optionId, values, displayName }),
  },
  importJson: (json: string) => post<{ created: McpServer[]; skipped: string[] }>('/mcp/import', { json }),
  exportJson: () => get<Record<string, unknown>>('/mcp/export'),
  toolsets: () => get<Toolset[]>('/mcp/toolsets'),
  providers: {
    list: () => get<Provider[]>('/providers'),
    create: (r: ProviderRequest) => post<Provider>('/providers', r),
    update: (id: string, r: ProviderRequest) => put<Provider>(`/providers/${id}`, r),
    remove: (id: string) => del(`/providers/${id}`),
    refreshModels: (id: string) => post<Provider>(`/providers/${id}/models/refresh`),
    test: (id: string) => post<{ ok: boolean; message: string; latencyMs: number }>(`/providers/${id}/test`),
  },
  settings: {
    get: () => get<AppSettings>('/settings'),
    update: (s: AppSettings) => put<AppSettings>('/settings', s),
    defaultPrompt: () => get<{ systemPrompt: string }>('/settings/default-system-prompt'),
  },
  workspaces: {
    list: () => get<Workspace[]>('/workspaces'),
    create: (w: Omit<Workspace, 'id' | 'isDefault'>) => post<Workspace>('/workspaces', w),
    update: (id: string, w: Omit<Workspace, 'id' | 'isDefault'>) => put<Workspace>(`/workspaces/${id}`, w),
    remove: (id: string) => del(`/workspaces/${id}`),
  },
  conversations: {
    list: (q?: string, workspaceId?: string | null) => {
      const p = new URLSearchParams()
      if (q) p.set('q', q)
      if (workspaceId) p.set('workspaceId', workspaceId)
      const qs = p.toString()
      return get<ConversationSummary[]>(`/conversations${qs ? `?${qs}` : ''}`)
    },
    create: (b: { title?: string; workspaceId?: string | null; providerId?: string | null; model?: string | null; serverIds?: string[] }) =>
      post<ConversationSummary>('/conversations', b),
    get: (id: string) => get<ConversationDetail>(`/conversations/${id}`),
    update: (id: string, b: Partial<Pick<ConversationSummary, 'title' | 'pinned' | 'workspaceId' | 'providerId' | 'model' | 'serverIds'>>) =>
      patch<ConversationSummary>(`/conversations/${id}`, b),
    remove: (id: string) => del(`/conversations/${id}`),
    stop: (id: string) => post<void>(`/conversations/${id}/stop`),
  },
}

export interface StreamHandlers {
  onEvent: (event: string, data: any) => void
}

/** POST + Server-Sent Events reader (EventSource can't POST). */
export async function streamChat(
  conversationId: string,
  body: { content: string; workspaceId?: string | null; providerId?: string | null; model?: string | null; serverIds?: string[]; regenerate?: boolean },
  { onEvent }: StreamHandlers,
  signal: AbortSignal,
) {
  const res = await fetch(`/api/conversations/${conversationId}/messages/stream`, {
    method: 'POST',
    headers: auth.headers({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(body),
    signal,
  })
  if (res.status === 401) {
    sessionExpired()
    throw new ApiError(401, 'Session expired')
  }
  if (!res.ok || !res.body) {
    let message = `${res.status} ${res.statusText}`
    try {
      const data = await res.json()
      message = data.detail || message
    } catch { /* ignore */ }
    throw new ApiError(res.status, message)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buffer.search(/\r?\n\r?\n/)) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx).replace(/^\r?\n\r?\n/, '')
      let event = 'message'
      const data: string[] = []
      for (const line of raw.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
      }
      if (data.length) {
        try {
          onEvent(event, JSON.parse(data.join('\n')))
        } catch {
          onEvent(event, data.join('\n'))
        }
      }
    }
  }
}
