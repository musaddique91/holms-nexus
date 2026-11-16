import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { motion } from 'motion/react'
import { AlertCircle, Download, Pencil, Plus, RefreshCw, Telescope, Trash2, Terminal, Globe, Radio } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { useServers } from '@/lib/queries'
import type { McpServer } from '@/lib/types'
import { cn, relativeTime } from '@/lib/utils'
import { useUi } from '@/store/ui'
import { Button } from '@/components/ui/Button'
import { Switch } from '@/components/ui/Switch'
import { Badge, StatusDot, statusMeta } from '@/components/ui/Badge'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { confirm } from '@/components/ui/Confirm'
import { Spinner } from '@/components/ui/Spinner'
import { PanelHeader } from './SettingsDialog'
import { AddServerDialog } from './AddServerDialog'
import { ServerFormDialog } from './ServerForm'
import { ServerExplorer } from './ServerExplorer'

export const transportMeta = {
  STDIO: { label: 'Local', icon: Terminal },
  STREAMABLE_HTTP: { label: 'HTTP', icon: Globe },
  SSE: { label: 'SSE', icon: Radio },
} as const

export function ServersPanel() {
  const { data: servers, isLoading } = useServers()
  const { addServerOpen, setAddServerOpen, explorerServerId, setExplorerServerId } = useUi()
  const [editing, setEditing] = useState<McpServer | null>(null)

  const exportConfig = async () => {
    const data = await api.exportJson()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'mcp-servers.json'
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return (
    <div>
      <PanelHeader
        title="MCP Servers"
        description="Connect data sources. Every tool they expose becomes available to the assistant."
        actions={
          <>
            {!!servers?.length && (
              <Button variant="ghost" size="sm" onClick={exportConfig}>
                <Download className="size-3.5" /> Export
              </Button>
            )}
            <Button variant="primary" size="sm" onClick={() => setAddServerOpen(true)}>
              <Plus className="size-4" /> Add server
            </Button>
          </>
        }
      />
      <div className="p-6">
        {isLoading ? (
          <div className="flex justify-center py-16 text-muted"><Spinner className="size-6" /></div>
        ) : !servers?.length ? (
          <EmptyServers onAdd={() => setAddServerOpen(true)} />
        ) : (
          <div className="grid gap-3 xl:grid-cols-2">
            {servers.map((s, i) => (
              <motion.div key={s.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}>
                <ServerCard server={s} onEdit={() => setEditing(s)} onExplore={() => setExplorerServerId(s.id)} />
              </motion.div>
            ))}
            <button
              onClick={() => setAddServerOpen(true)}
              className="flex min-h-[140px] flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-line-strong text-sm text-muted transition hover:border-accent/50 hover:bg-accent-soft hover:text-accent"
            >
              <Plus className="size-5" /> Add another server
            </button>
          </div>
        )}
      </div>
      <AddServerDialog open={addServerOpen} onClose={() => setAddServerOpen(false)} />
      <ServerFormDialog server={editing} open={!!editing} onClose={() => setEditing(null)} />
      <ServerExplorer serverId={explorerServerId} onClose={() => setExplorerServerId(null)} />
    </div>
  )
}

function EmptyServers({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="flex flex-col items-center rounded-3xl border border-dashed border-line-strong px-6 py-16 text-center">
      <div className="relative mb-5">
        <div className="absolute inset-0 rounded-full bg-gradient-accent opacity-30 blur-2xl" />
        <div className="relative flex size-16 items-center justify-center rounded-2xl bg-gradient-accent text-white shadow-lg">
          <Radio className="size-7" />
        </div>
      </div>
      <h3 className="text-lg font-semibold">No servers connected</h3>
      <p className="mt-1 max-w-sm text-sm text-muted">
        Install one from the catalog in a click, add your own local or remote server, or paste a Claude Desktop config.
      </p>
      <Button variant="primary" className="mt-5" onClick={onAdd}><Plus className="size-4" /> Add your first server</Button>
    </div>
  )
}

function ServerCard({ server: s, onEdit, onExplore }: { server: McpServer; onEdit: () => void; onExplore: () => void }) {
  const qc = useQueryClient()
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['servers'] })
    qc.invalidateQueries({ queryKey: ['toolsets'] })
  }
  const toggle = useMutation({ mutationFn: (v: boolean) => api.servers.setEnabled(s.id, v), onSuccess: refresh })
  const reconnect = useMutation({
    mutationFn: () => api.servers.reconnect(s.id),
    onSuccess: (r) => {
      refresh()
      if (r.status === 'CONNECTED') toast.success(`${r.name} connected`, { description: `${r.toolCount} tools available` })
      else toast.error(`${r.name} failed to connect`, { description: r.error ?? undefined })
    },
  })
  const remove = async () => {
    if (!(await confirm({ title: `Remove ${s.name}?`, message: 'The server will be disconnected and its configuration deleted.', confirmLabel: 'Remove', danger: true }))) return
    await api.servers.remove(s.id)
    refresh()
    toast.success(`${s.name} removed`)
  }
  const T = transportMeta[s.transport]
  const meta = statusMeta[s.status]
  const target = s.transport === 'STDIO' ? [s.command, ...s.args].join(' ') : s.url

  return (
    <div className={cn('group relative flex h-full flex-col rounded-2xl border bg-elev p-4 transition hover:shadow-soft', s.status === 'ERROR' ? 'border-err/25' : 'border-line hover:border-line-strong')}>
      <div className="flex items-start gap-3">
        <ServerIcon name={s.name} icon={s.icon} color={s.color} size="lg" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h3 className="truncate font-semibold">{s.name}</h3>
            <Badge><T.icon className="size-3" />{T.label}</Badge>
          </div>
          <div className={cn('mt-0.5 flex items-center gap-1.5 text-xs', meta.text)}>
            <StatusDot status={s.status} />
            {meta.label}
            {s.status === 'CONNECTED' && (
              <span className="text-muted">
                · {s.serverName}{s.serverVersion ? ` v${s.serverVersion}` : ''} · {s.connectedAt && relativeTime(s.connectedAt)}
              </span>
            )}
          </div>
        </div>
        <Switch checked={s.enabled} onChange={(v) => toggle.mutate(v)} disabled={toggle.isPending} label={`Enable ${s.name}`} />
      </div>

      {s.description && <p className="mt-3 line-clamp-2 text-sm text-fg-2">{s.description}</p>}
      <code className="mt-2 block truncate rounded-lg bg-elev-2 px-2.5 py-1.5 font-mono text-xs text-muted" title={target ?? ''}>{target}</code>

      {s.status === 'ERROR' && s.error && (
        <div className="mt-2 flex items-start gap-2 rounded-lg bg-err/5 px-2.5 py-2 text-xs text-err">
          <AlertCircle className="mt-px size-3.5 shrink-0" />
          <span className="line-clamp-3 break-words">{s.error}</span>
        </div>
      )}

      <div className="mt-auto flex items-center gap-3 pt-3 text-xs text-muted">
        <Stat n={s.toolCount} label="tools" />
        <Stat n={s.resourceCount} label="resources" />
        <Stat n={s.promptCount} label="prompts" />
        <div className="ml-auto flex items-center gap-0.5">
          <Button size="sm" variant="ghost" onClick={onExplore} disabled={s.status !== 'CONNECTED' && !s.toolCount}>
            <Telescope className="size-3.5" /> Explore
          </Button>
          <Button size="icon-sm" variant="ghost" onClick={() => reconnect.mutate()} disabled={!s.enabled || reconnect.isPending} title="Reconnect" aria-label="Reconnect">
            <RefreshCw className={cn('size-3.5', (reconnect.isPending || s.status === 'CONNECTING') && 'animate-spin')} />
          </Button>
          <Button size="icon-sm" variant="ghost" onClick={onEdit} title="Edit" aria-label="Edit"><Pencil className="size-3.5" /></Button>
          <Button size="icon-sm" variant="ghost" onClick={remove} title="Remove" aria-label="Remove" className="hover:text-err"><Trash2 className="size-3.5" /></Button>
        </div>
      </div>
    </div>
  )
}

function Stat({ n, label }: { n: number; label: string }) {
  return (
    <span className="whitespace-nowrap">
      <span className="font-semibold text-fg tabular-nums">{n}</span> {label}
    </span>
  )
}
