import { useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import { useProviders, useServers, useSettings, useWorkspaces } from './queries'
import type { ConversationDetail, ConversationSummary } from './types'
import { useChat } from '@/store/chat'
import { useUi } from '@/store/ui'

/**
 * Resolves which servers / provider / model the composer uses — the conversation's own choices,
 * or (for a new chat) the draft with sensible defaults.
 */
export function useSelection(conversation?: ConversationSummary) {
  const qc = useQueryClient()
  const { data: servers = [] } = useServers()
  const { data: providers = [] } = useProviders()
  const { data: settings } = useSettings()
  const { data: workspaces = [] } = useWorkspaces()
  const { draft, setDraft } = useChat()
  const selectedWorkspaceId = useUi((s) => s.workspaceId)

  const workspace =
    workspaces.find((w) => w.id === (conversation?.workspaceId ?? selectedWorkspaceId)) ??
    workspaces.find((w) => w.isDefault) ??
    workspaces[0]
  // Servers this chat may use: the workspace's enabled servers.
  const workspaceServers = servers.filter((s) => s.enabled && (!workspace || workspace.allServers || workspace.serverIds.includes(s.id)))
  const enabledIds = workspaceServers.map((s) => s.id)
  const ready = providers.filter((p) => p.enabled && p.ready)
  const fallbackProvider =
    ready.find((p) => p.id === settings?.defaultProviderId) ??
    [...ready].sort((a, b) => Number(a.type === 'OLLAMA') - Number(b.type === 'OLLAMA'))[0]

  const providerId = conversation?.providerId ?? draft.providerId ?? fallbackProvider?.id ?? null
  const provider = providers.find((p) => p.id === providerId)
  const model =
    (conversation ? conversation.model : draft.model) ??
    (provider && provider.id === settings?.defaultProviderId && settings?.defaultModel ? settings.defaultModel : null) ??
    provider?.defaultModel ??
    null
  const serverIds = (conversation ? conversation.serverIds : draft.serverIds ?? enabledIds).filter((id) =>
    enabledIds.includes(id),
  )

  const persist = (patch: Partial<ConversationSummary>) => {
    if (!conversation) return
    qc.setQueryData<ConversationDetail>(['conversation', conversation.id], (old) =>
      old ? { ...old, conversation: { ...old.conversation, ...patch } } : old,
    )
    api.conversations.update(conversation.id, patch).catch(() => undefined)
  }

  return {
    servers: workspaceServers,
    workspace,
    workspaces,
    providers,
    serverIds,
    providerId,
    model,
    setServerIds: (ids: string[]) => (conversation ? persist({ serverIds: ids }) : setDraft({ serverIds: ids })),
    setModel: (pid: string, m: string) => (conversation ? persist({ providerId: pid, model: m }) : setDraft({ providerId: pid, model: m })),
    hasProvider: ready.length > 0,
  }
}
