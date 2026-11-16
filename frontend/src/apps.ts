import { MessagesSquare, ShieldAlert, type LucideIcon } from 'lucide-react'

/** The two applications behind the one MVX Nexus login. */
export type AppId = 'holmes' | 'mcp'

export interface AppInfo {
  id: AppId
  name: string
  tagline: string
  description: string
  path: string
  icon: LucideIcon
}

export const APPS: AppInfo[] = [
  {
    id: 'holmes',
    name: 'Intelligence',
    tagline: 'AI-powered incident response',
    description: 'Monitors every service for errors, identifies the root cause, proposes fix pull requests, and alerts your team.',
    path: '/holmes',
    icon: ShieldAlert,
  },
  {
    id: 'mcp',
    name: 'MCP',
    tagline: 'Chat with your data',
    description: 'Ask questions in plain language across your MCP servers — databases, GitHub, files — with tables and charts.',
    path: '/mcp',
    icon: MessagesSquare,
  },
]

const LAST_APP = 'nexus.lastApp'

export function rememberApp(id: AppId) {
  try {
    localStorage.setItem(LAST_APP, id)
  } catch {
    /* ignore */
  }
}

export function lastApp(): AppId | null {
  try {
    const v = localStorage.getItem(LAST_APP)
    return v === 'holmes' || v === 'mcp' ? v : null
  } catch {
    return null
  }
}
