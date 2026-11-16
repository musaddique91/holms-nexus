export type IncidentStatus =
  | 'NEW'
  | 'INVESTIGATING'
  | 'INVESTIGATED'
  | 'FIXING'
  | 'PR_OPENED'
  | 'FAILED'
  | 'RESOLVED'
  | 'IGNORED'

export interface Incident {
  id: number
  serviceName: string
  environment: string | null
  title: string
  errorType: string | null
  level: string | null
  status: IncidentStatus
  severity: string | null
  occurrenceCount: number
  firstSeenAt: string
  lastSeenAt: string
  statusChangedAt: string
  sampleMessage: string | null
  sampleStackTrace: string | null
  logger: string | null
  host: string | null
  source: string | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface ToolCall {
  sequence: number
  toolName: string
  arguments: string
  output: string
  success: boolean
  durationMs: number
  createdAt: string
}

export interface Investigation {
  id: number
  incidentId: number | null
  kind: 'INCIDENT' | 'CHAT'
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  provider: string
  model: string
  question: string | null
  summary: string | null
  rootCause: string | null
  evidence: string[]
  suggestedFix: string | null
  confidence: string | null
  severity: string | null
  category: string | null
  codeFixRecommended: boolean
  suspectedFiles: string[]
  report: string | null
  steps: number
  toolCallCount: number
  inputTokens: number
  outputTokens: number
  error: string | null
  startedAt: string
  finishedAt: string | null
  toolCalls: ToolCall[]
}

export interface Remediation {
  id: number
  investigationId: number | null
  status: 'RUNNING' | 'PR_OPENED' | 'NO_CHANGE' | 'REJECTED' | 'FAILED'
  model: string
  branch: string | null
  prUrl: string | null
  prNumber: string | null
  title: string | null
  description: string | null
  filesChanged: string[]
  diff: string | null
  reason: string | null
  error: string | null
  startedAt: string
  finishedAt: string | null
  toolCalls: ToolCall[]
}

export interface IncidentDetail {
  incident: Incident
  investigations: Investigation[]
  remediations: Remediation[]
}

export type GitProvider = 'GITHUB' | 'GITLAB' | 'NONE'

export interface Service {
  id: number
  name: string
  displayName: string | null
  aliases: string[]
  environment: string | null
  repoUrl: string | null
  gitProvider: GitProvider
  defaultBranch: string
  codePath: string | null
  runbook: string | null
  autoFix: boolean
  slackChannel: string | null
  emailRecipients: string[]
  telegramChatId: string | null
  /** Write-only: the API only says whether the service has its own git token. */
  gitTokenSet?: boolean
  gitTokenHint?: string | null
}

export type ServiceInput = Omit<Service, 'id' | 'gitTokenSet' | 'gitTokenHint'> & {
  /** null/undefined = keep the saved token, '' = remove it, anything else = replace it. */
  gitToken?: string | null
}

export interface Toolset {
  name: string
  description: string
  enabled: boolean
  healthy: boolean | null
  message: string | null
  tools: string[]
}

export interface Dashboard {
  byStatus: Record<string, number>
  newIncidents24h: number
  investigations24h: number
  pullRequestsOpened: number
  topServices7d: { service: string; incidents: number; occurrences: number }[]
  enabledToolsets: string[]
  notificationChannels: string[]
}

export interface LlmSettings {
  provider: string
  model: string
  fixModel: string | null
  baseUrl: string | null
  defaultBaseUrl: string | null
  azureDeployment: string | null
  maxTokens: number
  maxTokensParam: string
  temperature: number
  apiKeySet: boolean
  apiKeySource: 'database' | 'environment' | 'none'
  apiKeyHint: string | null
  updatedAt: string | null
  updatedBy: string | null
  providers: string[]
  /** MVX Nexus: the shared provider in use (null = standalone settings) and the shared Models list. */
  providerId: string | null
  providerName: string | null
  sharedProviders: SharedProvider[]
}

export interface SharedProvider {
  id: string
  name: string
  type: 'ANTHROPIC' | 'OPENAI' | 'OLLAMA'
  baseUrl: string | null
  hasApiKey: boolean
  enabled: boolean
  defaultModel: string
  models: string[]
}

/** Optional strings: undefined/null = keep, "" = clear. apiKey is write-only. */
export interface LlmSettingsUpdate {
  provider?: string
  model?: string
  fixModel?: string | null
  baseUrl?: string | null
  azureDeployment?: string | null
  maxTokens?: number
  maxTokensParam?: string
  temperature?: number
  apiKey?: string | null
  /** Shared provider id; undefined = keep, "" = use the standalone settings. */
  providerId?: string | null
}

export interface LlmTestResult {
  ok: boolean
  toolCalling: boolean
  latencyMs: number
  model: string
  detail: string
}

export type Channel = 'slack' | 'email' | 'telegram'
export type MinSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

export interface NotificationSettings {
  minSeverity: MinSeverity
  slack: { enabled: boolean; webhookSet: boolean; webhookHint: string | null; botTokenSet: boolean; botTokenHint: string | null; channel: string | null }
  email: { enabled: boolean; smtpHost: string | null; smtpPort: number; smtpUsername: string | null; smtpPasswordSet: boolean; startTls: boolean; from: string | null; to: string[] }
  telegram: { enabled: boolean; botTokenSet: boolean; botTokenHint: string | null; chatId: string | null }
  channels: { channel: Channel; switchedOn: boolean; missing: string | null }[]
  source: 'database' | 'environment'
  updatedAt: string | null
  updatedBy: string | null
}

/** Partial update: omitted/null fields keep the saved value; secrets: '' removes. */
export interface NotificationSettingsUpdate {
  minSeverity?: MinSeverity
  slack?: { enabled?: boolean; webhookUrl?: string | null; botToken?: string | null; channel?: string }
  email?: { enabled?: boolean; smtpHost?: string; smtpPort?: number; smtpUsername?: string; smtpPassword?: string | null; startTls?: boolean; from?: string; to?: string[] }
  telegram?: { enabled?: boolean; botToken?: string | null; chatId?: string }
}

export interface NotificationTestResult {
  ok: boolean
  channel: Channel
  latencyMs: number
  detail: string
}
