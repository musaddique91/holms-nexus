export type Transport = 'STDIO' | 'STREAMABLE_HTTP' | 'SSE'
export type ConnStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR'
export type ProviderType = 'ANTHROPIC' | 'OPENAI' | 'OLLAMA'

export interface McpServer {
  id: string
  name: string
  description?: string | null
  transport: Transport
  command?: string | null
  args: string[]
  env: Record<string, string>
  url?: string | null
  headers: Record<string, string>
  enabled: boolean
  icon?: string | null
  color?: string | null
  catalogId?: string | null
  status: ConnStatus
  error?: string | null
  toolCount: number
  resourceCount: number
  promptCount: number
  serverName?: string | null
  serverVersion?: string | null
  instructions?: string | null
  connectedAt?: string | null
  createdAt: string
}

export interface ServerRequest {
  name: string
  description?: string | null
  transport: Transport
  command?: string | null
  args?: string[]
  env?: Record<string, string>
  url?: string | null
  headers?: Record<string, string>
  enabled?: boolean
  icon?: string | null
  color?: string | null
  catalogId?: string | null
}

export interface JsonSchema {
  type?: string | string[]
  title?: string
  description?: string
  properties?: Record<string, JsonSchema>
  required?: string[]
  enum?: unknown[]
  items?: JsonSchema
  default?: unknown
  [k: string]: unknown
}

export interface Tool {
  name: string
  title?: string | null
  description?: string | null
  inputSchema?: JsonSchema
  readOnly: boolean
  destructive: boolean
}

export interface Toolset {
  serverId: string
  serverName: string
  icon?: string | null
  color?: string | null
  status: ConnStatus
  tools: Tool[]
}

export interface Resource { uri: string; name: string; title?: string; description?: string; mimeType?: string }
export interface PromptArg { name: string; description?: string; required: boolean }
export interface Prompt { name: string; title?: string; description?: string; arguments: PromptArg[] }
export interface LogLine { at: string; level: string; message: string }

export interface CatalogParam { key: string; label: string; placeholder?: string; secret: boolean; required: boolean; help?: string }
export interface CatalogEntry {
  id: string
  name: string
  description: string
  category: string
  icon: string
  color: string
  transport: Transport
  command?: string
  args: string[]
  env: Record<string, string>
  url?: string
  headers: Record<string, string>
  params: CatalogParam[]
  docsUrl?: string
  runtime: string
}

export interface Provider {
  id: string
  name: string
  type: ProviderType
  hasApiKey: boolean
  apiKeyHint?: string | null
  baseUrl?: string | null
  defaultModel: string
  models: string[]
  enabled: boolean
  ready: boolean
}

export interface ProviderRequest {
  name: string
  type: ProviderType
  apiKey?: string
  baseUrl?: string | null
  defaultModel: string
  models?: string[]
  enabled?: boolean
}

export interface AppSettings {
  systemPrompt: string
  temperature: number
  maxTokens: number
  defaultProviderId?: string | null
  defaultModel?: string | null
  agentMode: 'multi' | 'single'
  visualizer: boolean
  maxParallelAgents: number
}

export interface Workspace {
  id: string
  name: string
  description?: string | null
  icon?: string | null
  color?: string | null
  allServers: boolean
  serverIds: string[]
  instructions?: string | null
  isDefault: boolean
}

export interface ConversationSummary {
  id: string
  title: string
  pinned: boolean
  workspaceId?: string | null
  providerId?: string | null
  model?: string | null
  serverIds: string[]
  createdAt: string
  updatedAt: string
}

export type ToolStatus = 'running' | 'ok' | 'error'
export interface TextPart { type: 'text'; text: string }
export interface ToolPart {
  type: 'tool'
  id: string
  serverId: string
  server: string
  tool: string
  args: Record<string, unknown>
  status: ToolStatus
  result?: string
  durationMs?: number
}
export type AgentRole = 'planner' | 'retriever' | 'analyst' | 'visualizer' | 'assistant'
export type AgentStatus = 'running' | 'ok' | 'error' | 'skipped' | 'cancelled'

export interface AgentRunPart {
  id: string
  role: AgentRole
  name: string
  goal?: string
  serverId?: string
  server?: string
  status: AgentStatus
  startedAt: number
  durationMs?: number
  summary?: string
  output?: string
  tools: ToolPart[]
}

export interface PlanInfo {
  intent: string
  steps: { server: string; goal: string }[]
  visualize: boolean
  chartHint?: string
  presentation?: string
  fallback?: boolean
}

export interface WorkflowPart { type: 'workflow'; plan?: PlanInfo; agents: AgentRunPart[] }
export interface ChartPart { type: 'chart'; id: string; title: string; options: Record<string, unknown> }

export type Part = TextPart | ToolPart | WorkflowPart | ChartPart

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  parts: Part[]
  model?: string | null
  durationMs?: number | null
  error: boolean
  createdAt: string
}

export interface ConversationDetail {
  conversation: ConversationSummary
  messages: ChatMessage[]
}

export interface RegistryField { key: string; label: string; description?: string | null; required: boolean; secret: boolean; defaultValue?: string | null }
export interface RegistryOption { id: string; kind: 'remote' | 'npm' | 'pypi' | 'oci'; label: string; transport: Transport; preview: string; fields: RegistryField[] }
export interface RegistryServer {
  name: string
  title: string
  description?: string | null
  version?: string | null
  websiteUrl?: string | null
  repositoryUrl?: string | null
  iconUrl?: string | null
  options: RegistryOption[]
}
