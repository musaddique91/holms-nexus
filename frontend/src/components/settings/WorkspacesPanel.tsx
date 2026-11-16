import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Check, Layers, Pencil, Plus, Star, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { useServers, useWorkspaces } from '@/lib/queries'
import type { Workspace } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useUi } from '@/store/ui'
import { Button } from '@/components/ui/Button'
import { Badge, StatusDot } from '@/components/ui/Badge'
import { Field, Input, Textarea } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Switch } from '@/components/ui/Switch'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { confirm } from '@/components/ui/Confirm'
import { PanelHeader } from './SettingsDialog'

const COLORS = ['#e3150e', '#141110', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ec4899', '#3b82f6']
const ICONS = ['layers', 'database', 'github', 'globe', 'folder', 'container', 'brain', 'sparkles', 'monitor', 'notebook']

export function WorkspacesPanel() {
  const { data: workspaces = [] } = useWorkspaces()
  const { data: servers = [] } = useServers()
  const { workspaceId, setWorkspaceId } = useUi()
  const qc = useQueryClient()
  const [editing, setEditing] = useState<Workspace | 'new' | null>(null)
  const active = workspaces.find((w) => w.id === workspaceId) ?? workspaces.find((w) => w.isDefault)

  const remove = async (w: Workspace) => {
    if (!(await confirm({ title: `Delete “${w.name}”?`, message: 'Its chats move to no workspace and show under the default one.', confirmLabel: 'Delete', danger: true }))) return
    await api.workspaces.remove(w.id)
    if (w.id === workspaceId) setWorkspaceId(null)
    qc.invalidateQueries({ queryKey: ['workspaces'] })
    qc.invalidateQueries({ queryKey: ['conversations'] })
  }

  return (
    <div>
      <PanelHeader
        title="Workspaces"
        description="Group MCP servers into focused workspaces, each with its own instructions and chat history."
        actions={<Button variant="primary" size="sm" onClick={() => setEditing('new')}><Plus className="size-4" /> New workspace</Button>}
      />
      <div className="grid gap-3 p-6 lg:grid-cols-2">
        {workspaces.map((w) => {
          const members = servers.filter((s) => s.enabled && (w.allServers || w.serverIds.includes(s.id)))
          return (
            <div key={w.id} className={cn('flex flex-col rounded-2xl border bg-elev p-4 transition', w.id === active?.id ? 'border-accent/40 ring-4 ring-accent/10' : 'border-line')}>
              <div className="flex items-start gap-3">
                <ServerIcon name={w.name} icon={w.icon ?? 'layers'} color={w.color} size="lg" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="truncate font-semibold">{w.name}</h3>
                    {w.isDefault && <Badge><Star className="size-3" /> Default</Badge>}
                    {w.id === active?.id && <Badge tone="accent">Active</Badge>}
                  </div>
                  <p className="line-clamp-2 text-sm text-muted">{w.description || (w.allServers ? 'Every enabled MCP server' : 'Custom server selection')}</p>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {members.length === 0 && <span className="text-xs text-muted">No servers yet</span>}
                {members.slice(0, 8).map((s) => (
                  <span key={s.id} className="flex items-center gap-1.5 rounded-full border border-line bg-elev-2/60 py-0.5 pr-2 pl-0.5 text-xs">
                    <ServerIcon name={s.name} icon={s.icon} color={s.color} size="xs" className="rounded-full" /> {s.name} <StatusDot status={s.status} />
                  </span>
                ))}
                {members.length > 8 && <Badge>+{members.length - 8}</Badge>}
              </div>
              {w.instructions && <p className="mt-3 line-clamp-2 rounded-lg bg-elev-2 px-2.5 py-1.5 text-xs text-fg-2">{w.instructions}</p>}
              <div className="mt-auto flex items-center gap-1 pt-4">
                {w.id !== active?.id && (
                  <Button size="sm" variant="outline" onClick={() => setWorkspaceId(w.id)}><Check className="size-3.5" /> Switch to</Button>
                )}
                <div className="ml-auto flex">
                  <Button size="icon-sm" variant="ghost" onClick={() => setEditing(w)} aria-label="Edit"><Pencil className="size-3.5" /></Button>
                  {!w.isDefault && <Button size="icon-sm" variant="ghost" onClick={() => remove(w)} aria-label="Delete" className="hover:text-err"><Trash2 className="size-3.5" /></Button>}
                </div>
              </div>
            </div>
          )
        })}
      </div>
      <WorkspaceDialog workspace={editing} onClose={() => setEditing(null)} />
    </div>
  )
}

function WorkspaceDialog({ workspace, onClose }: { workspace: Workspace | 'new' | null; onClose: () => void }) {
  const qc = useQueryClient()
  const { data: servers = [] } = useServers()
  const { setWorkspaceId } = useUi()
  const existing = workspace && workspace !== 'new' ? workspace : null
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [icon, setIcon] = useState('layers')
  const [color, setColor] = useState(COLORS[0])
  const [allServers, setAllServers] = useState(false)
  const [serverIds, setServerIds] = useState<string[]>([])
  const [instructions, setInstructions] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!workspace) return
    setName(existing?.name ?? '')
    setDescription(existing?.description ?? '')
    setIcon(existing?.icon ?? 'layers')
    setColor(existing?.color ?? COLORS[0])
    setAllServers(existing?.allServers ?? false)
    setServerIds(existing?.serverIds ?? [])
    setInstructions(existing?.instructions ?? '')
  }, [workspace]) // eslint-disable-line react-hooks/exhaustive-deps

  const save = async () => {
    setBusy(true)
    try {
      const body = { name, description, icon, color, allServers, serverIds, instructions }
      const saved = existing ? await api.workspaces.update(existing.id, body) : await api.workspaces.create(body)
      qc.invalidateQueries({ queryKey: ['workspaces'] })
      if (!existing) setWorkspaceId(saved.id)
      toast.success(existing ? 'Workspace updated' : `Switched to ${saved.name}`)
      onClose()
    } catch (e) {
      toast.error('Could not save workspace', { description: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={!!workspace} onClose={onClose} title={existing ? `Edit ${existing.name}` : 'New workspace'} description="Pick the MCP servers agents can use here." className="sm:max-w-2xl">
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-6 py-3">
        <div className="flex items-start gap-3">
          <ServerIcon name={name || 'W'} icon={icon} color={color} size="lg" />
          <div className="grid flex-1 gap-3 sm:grid-cols-2">
            <Field label="Name" required><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Sales analytics" autoFocus /></Field>
            <Field label="Description"><Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="CRM + warehouse data" /></Field>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex gap-1.5">
            {COLORS.map((c) => (
              <button key={c} onClick={() => setColor(c)} className={cn('size-6 rounded-full border-2 transition', color === c ? 'border-fg scale-110' : 'border-transparent')} style={{ background: c }} aria-label={c} />
            ))}
          </div>
          <div className="flex flex-wrap gap-1">
            {ICONS.map((i) => (
              <button key={i} onClick={() => setIcon(i)} className={cn('rounded-lg p-0.5 transition', icon === i ? 'ring-2 ring-accent' : 'opacity-60 hover:opacity-100')}>
                <ServerIcon name={i} icon={i} color={color} size="xs" />
              </button>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-line">
          <div className="flex items-center justify-between border-b border-line px-3 py-2.5">
            <div>
              <div className="text-sm font-medium">Include every server</div>
              <div className="text-xs text-muted">Servers added later join automatically</div>
            </div>
            <Switch checked={allServers} onChange={setAllServers} />
          </div>
          {!allServers && (
            <div className="max-h-64 space-y-0.5 overflow-y-auto p-1.5">
              {servers.length === 0 && <p className="px-2 py-3 text-sm text-muted">Add MCP servers first.</p>}
              {servers.map((s) => {
                const on = serverIds.includes(s.id)
                return (
                  <button key={s.id} onClick={() => setServerIds(on ? serverIds.filter((x) => x !== s.id) : [...serverIds, s.id])}
                    className="flex w-full items-center gap-2.5 rounded-lg px-2 py-1.5 text-left transition hover:bg-elev-2">
                    <ServerIcon name={s.name} icon={s.icon} color={s.color} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm">{s.name}</span>
                      <span className="flex items-center gap-1.5 text-xs text-muted"><StatusDot status={s.status} /> {s.toolCount} tools{!s.enabled && ' · disabled'}</span>
                    </span>
                    <span className={cn('flex size-5 items-center justify-center rounded-md border', on ? 'border-transparent bg-gradient-accent text-white' : 'border-line-strong')}>
                      {on && <Check className="size-3.5" strokeWidth={3} />}
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        <Field label="Workspace instructions" hint="Given to every agent in this workspace: domain terms, KPIs, default filters, tone.">
          <Textarea className="min-h-[96px]" value={instructions} onChange={(e) => setInstructions(e.target.value)} placeholder="Revenue means net revenue in USD. Fiscal year starts in April. Exclude test accounts (email ends with @example.com)." />
        </Field>
      </div>
      <div className="flex justify-end gap-2 border-t border-line px-6 py-4">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" onClick={save} loading={busy} disabled={!name.trim() || (!allServers && serverIds.length === 0)}>
          <Layers className="size-4" /> {existing ? 'Save' : 'Create workspace'}
        </Button>
      </div>
    </Modal>
  )
}
