import { Check, ChevronsUpDown, Plus, Settings2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useServers, useWorkspaces } from '@/lib/queries'
import type { Workspace } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useUi } from '@/store/ui'
import { useChat } from '@/store/chat'
import { Popover } from '@/components/ui/Popover'
import { ServerIcon } from '@/components/ui/ServerIcon'

export function workspaceServerCount(w: Workspace, enabledIds: string[]) {
  return w.allServers ? enabledIds.length : w.serverIds.filter((id) => enabledIds.includes(id)).length
}

/** Top-bar dropdown that scopes chats and data sources to an MCP workspace. */
export function WorkspaceSwitcher({ current }: { current?: Workspace }) {
  const { data: workspaces = [] } = useWorkspaces()
  const { data: servers = [] } = useServers()
  const { setWorkspaceId, openSettings } = useUi()
  const navigate = useNavigate()
  const enabledIds = servers.filter((s) => s.enabled).map((s) => s.id)
  if (!current) return null

  const pick = (w: Workspace) => {
    if (w.id !== current.id) {
      setWorkspaceId(w.id)
      useChat.getState().setDraft({ serverIds: null })
      navigate('/mcp')
    }
  }

  return (
    <Popover
      side="bottom"
      className="w-80"
      trigger={({ toggle, open }) => (
        <button
          onClick={toggle}
          className={cn('glass ring-focus flex h-9 max-w-64 items-center gap-2 rounded-xl border pr-2 pl-1.5 text-sm transition', open ? 'border-line-strong' : 'border-line hover:border-line-strong')}
        >
          <ServerIcon name={current.name} icon={current.icon ?? 'layers'} color={current.color} size="xs" className="size-6" />
          <span className="truncate font-medium">{current.name}</span>
          <ChevronsUpDown className="size-3.5 shrink-0 text-muted" />
        </button>
      )}
    >
      {(close) => (
        <div>
          <div className="px-2.5 pt-1.5 pb-2 text-[11px] font-semibold tracking-wider text-muted uppercase">MCP workspaces</div>
          <div className="max-h-80 space-y-0.5 overflow-y-auto">
            {workspaces.map((w) => (
              <button
                key={w.id}
                onClick={() => { pick(w); close() }}
                className={cn('flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left transition hover:bg-elev-2', w.id === current.id && 'bg-elev-2')}
              >
                <ServerIcon name={w.name} icon={w.icon ?? 'layers'} color={w.color} size="sm" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{w.name}</span>
                  <span className="block truncate text-xs text-muted">
                    {w.allServers ? 'All servers' : `${workspaceServerCount(w, enabledIds)} server${workspaceServerCount(w, enabledIds) === 1 ? '' : 's'}`}
                    {w.description ? ` · ${w.description}` : ''}
                  </span>
                </span>
                {w.id === current.id && <Check className="size-4 text-accent" />}
              </button>
            ))}
          </div>
          <div className="mt-1.5 flex gap-1 border-t border-line pt-1.5">
            <button onClick={() => { close(); openSettings('workspaces') }} className="flex flex-1 items-center gap-2 rounded-xl px-2.5 py-2 text-sm text-fg-2 transition hover:bg-elev-2 hover:text-fg">
              <Plus className="size-4" /> New workspace
            </button>
            <button onClick={() => { close(); openSettings('workspaces') }} className="flex flex-1 items-center gap-2 rounded-xl px-2.5 py-2 text-sm text-fg-2 transition hover:bg-elev-2 hover:text-fg">
              <Settings2 className="size-4" /> Manage
            </button>
          </div>
        </div>
      )}
    </Popover>
  )
}

