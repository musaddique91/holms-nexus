import { useQuery } from '@tanstack/react-query'
import { api } from './api'

export const useServers = () =>
  useQuery({
    queryKey: ['servers'],
    queryFn: api.servers.list,
    refetchInterval: (q) => (q.state.data?.some((s) => s.status === 'CONNECTING') ? 1500 : 15000),
  })

export const useToolsets = () =>
  useQuery({
    queryKey: ['toolsets'],
    queryFn: api.toolsets,
    refetchInterval: (q) => (q.state.data?.some((s) => s.status === 'CONNECTING') ? 1500 : 15000),
  })

export const useProviders = () => useQuery({ queryKey: ['providers'], queryFn: api.providers.list })
export const useSettings = () => useQuery({ queryKey: ['settings'], queryFn: api.settings.get })
export const useCatalog = () => useQuery({ queryKey: ['catalog'], queryFn: api.catalog.list, staleTime: Infinity })

export const useWorkspaces = () => useQuery({ queryKey: ['workspaces'], queryFn: api.workspaces.list })

export const useConversations = (q = '', workspaceId?: string | null) =>
  useQuery({
    queryKey: ['conversations', q, workspaceId ?? null],
    queryFn: () => api.conversations.list(q || undefined, workspaceId),
    placeholderData: (p) => p,
  })

export const useConversation = (id?: string) =>
  useQuery({ queryKey: ['conversation', id], queryFn: () => api.conversations.get(id!), enabled: !!id })
