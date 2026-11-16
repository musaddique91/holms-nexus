import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { ChevronRight, FileText, MessageSquareText, Play, ScrollText, Search, ShieldAlert, Eye, Wrench, X } from 'lucide-react'
import { api } from '@/lib/api'
import { useEscape } from '@/lib/useEscape'
import { useServers } from '@/lib/queries'
import type { Prompt, Resource, Tool } from '@/lib/types'
import { cn, formatDuration, prettyJson } from '@/lib/utils'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { Badge, StatusDot, statusMeta } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { CopyButton } from '@/components/chat/CodeBlock'
import { SchemaForm, coerceArgs } from './SchemaForm'

type Tab = 'tools' | 'resources' | 'prompts' | 'logs'

export function ServerExplorer({ serverId, onClose }: { serverId: string | null; onClose: () => void }) {
  const { data: servers = [] } = useServers()
  const server = servers.find((s) => s.id === serverId)
  const [tab, setTab] = useState<Tab>('tools')
  useEffect(() => setTab('tools'), [serverId])

  useEscape(!!serverId && !!server, onClose)

  const tabs: { id: Tab; label: string; icon: React.ElementType; count?: number }[] = [
    { id: 'tools', label: 'Tools', icon: Wrench, count: server?.toolCount },
    { id: 'resources', label: 'Resources', icon: FileText, count: server?.resourceCount },
    { id: 'prompts', label: 'Prompts', icon: MessageSquareText, count: server?.promptCount },
    { id: 'logs', label: 'Logs', icon: ScrollText },
  ]

  return createPortal(
    <AnimatePresence>
      {serverId && server && (
        <div className="fixed inset-0 z-[60]">
          <motion.div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.aside
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', stiffness: 380, damping: 38 }}
            className="absolute inset-y-0 right-0 flex w-full max-w-2xl flex-col border-l border-line-strong bg-elev shadow-2xl"
          >
            <div className="flex items-start gap-3 px-6 pt-5 pb-4">
              <ServerIcon name={server.name} icon={server.icon} color={server.color} size="lg" />
              <div className="min-w-0 flex-1">
                <h2 className="truncate text-lg font-semibold">{server.name}</h2>
                <div className={cn('flex items-center gap-1.5 text-xs', statusMeta[server.status].text)}>
                  <StatusDot status={server.status} /> {statusMeta[server.status].label}
                  {server.serverName && <span className="text-muted">· {server.serverName} {server.serverVersion}</span>}
                </div>
              </div>
              <button onClick={onClose} className="rounded-lg p-1.5 text-muted hover:bg-elev-2 hover:text-fg" aria-label="Close"><X className="size-4" /></button>
            </div>
            {server.instructions && (
              <p className="mx-6 mb-3 line-clamp-3 rounded-xl bg-elev-2 px-3 py-2 text-xs text-muted">{server.instructions}</p>
            )}
            <div className="flex shrink-0 gap-1 border-b border-line px-6">
              {tabs.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setTab(t.id)}
                  className={cn('relative flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium transition', tab === t.id ? 'text-fg' : 'text-muted hover:text-fg')}
                >
                  <t.icon className="size-4" /> {t.label}
                  {t.count != null && <span className="rounded-md bg-elev-2 px-1.5 text-[11px] tabular-nums">{t.count}</span>}
                  {tab === t.id && <motion.span layoutId="explorer-tab" className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-gradient-accent" />}
                </button>
              ))}
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto p-6">
              {tab === 'tools' && <ToolsTab serverId={server.id} />}
              {tab === 'resources' && <ResourcesTab serverId={server.id} />}
              {tab === 'prompts' && <PromptsTab serverId={server.id} />}
              {tab === 'logs' && <LogsTab serverId={server.id} />}
            </div>
          </motion.aside>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

function ToolsTab({ serverId }: { serverId: string }) {
  const { data: tools, isLoading } = useQuery({ queryKey: ['tools', serverId], queryFn: () => api.servers.tools(serverId) })
  const [q, setQ] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  if (isLoading) return <Center><Spinner className="size-6" /></Center>
  if (!tools?.length) return <Center>No tools exposed.</Center>
  const list = tools.filter((t) => (t.name + (t.description ?? '')).toLowerCase().includes(q.toLowerCase()))
  return (
    <div className="space-y-3">
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
        <Input className="pl-9" placeholder={`Search ${tools.length} tools…`} value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {list.map((t) => (
        <ToolRow key={t.name} serverId={serverId} tool={t} open={open === t.name} onToggle={() => setOpen(open === t.name ? null : t.name)} />
      ))}
    </div>
  )
}

function ToolRow({ serverId, tool, open, onToggle }: { serverId: string; tool: Tool; open: boolean; onToggle: () => void }) {
  const [values, setValues] = useState<Record<string, unknown>>({})
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<{ error: boolean; text: string; durationMs: number } | null>(null)
  const run = async () => {
    setRunning(true)
    setResult(null)
    try {
      setResult(await api.servers.callTool(serverId, tool.name, coerceArgs(tool.inputSchema, values)))
    } catch (e) {
      setResult({ error: true, text: (e as Error).message, durationMs: 0 })
    } finally {
      setRunning(false)
    }
  }
  const pretty = prettyJson(result?.text).pretty
  return (
    <div className={cn('rounded-2xl border transition', open ? 'border-line-strong bg-elev-2/40' : 'border-line')}>
      <button onClick={onToggle} className="flex w-full items-start gap-3 px-4 py-3 text-left">
        <Wrench className="mt-0.5 size-4 shrink-0 text-accent" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-sm font-medium">{tool.name}</span>
            {tool.readOnly && <Badge tone="ok"><Eye className="size-3" />read-only</Badge>}
            {tool.destructive && <Badge tone="err"><ShieldAlert className="size-3" />destructive</Badge>}
          </div>
          {tool.description && <p className={cn('mt-0.5 text-[13px] text-muted', !open && 'line-clamp-2')}>{tool.description}</p>}
        </div>
        <ChevronRight className={cn('mt-0.5 size-4 shrink-0 text-muted transition', open && 'rotate-90')} />
      </button>
      {open && (
        <div className="space-y-4 border-t border-line px-4 py-4">
          <SchemaForm schema={tool.inputSchema} values={values} onChange={setValues} />
          <Button variant="primary" size="sm" onClick={run} loading={running}>
            {!running && <Play className="size-3.5 fill-current" />} Run tool
          </Button>
          {result && (
            <div className={cn('rounded-xl border', result.error ? 'border-err/25' : 'border-line')}>
              <div className="flex items-center justify-between border-b border-line px-3 py-1.5 text-xs">
                <span className={result.error ? 'text-err' : 'text-ok'}>{result.error ? 'Error' : 'Success'} · {formatDuration(result.durationMs)}</span>
                <CopyButton text={pretty} />
              </div>
              <pre className="max-h-80 overflow-auto bg-code p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap break-words text-fg-2">{pretty || '(empty)'}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function ResourcesTab({ serverId }: { serverId: string }) {
  const { data, isLoading } = useQuery({ queryKey: ['resources', serverId], queryFn: () => api.servers.resources(serverId) })
  const [selected, setSelected] = useState<Resource | null>(null)
  const content = useQuery({
    queryKey: ['resource', serverId, selected?.uri],
    queryFn: () => api.servers.readResource(serverId, selected!.uri),
    enabled: !!selected,
  })
  if (isLoading) return <Center><Spinner className="size-6" /></Center>
  if (!data?.length) return <Center>This server exposes no resources.</Center>
  return (
    <div className="space-y-2">
      {data.map((r) => (
        <div key={r.uri} className="rounded-2xl border border-line">
          <button onClick={() => setSelected(selected?.uri === r.uri ? null : r)} className="flex w-full items-start gap-3 px-4 py-3 text-left">
            <FileText className="mt-0.5 size-4 shrink-0 text-accent-2" />
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium">{r.title || r.name}</div>
              <div className="truncate font-mono text-xs text-muted">{r.uri}</div>
              {r.description && <div className="mt-0.5 text-[13px] text-muted">{r.description}</div>}
            </div>
            {r.mimeType && <Badge>{r.mimeType}</Badge>}
          </button>
          {selected?.uri === r.uri && (
            <div className="border-t border-line p-3">
              {content.isLoading ? <Spinner /> : content.data?.map((c, i) => (
                <pre key={i} className="max-h-80 overflow-auto rounded-lg bg-code p-3 font-mono text-xs whitespace-pre-wrap text-fg-2">
                  {c.binary ? `[binary ${c.mimeType}, ${c.text.length} base64 chars]` : c.text}
                </pre>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function PromptsTab({ serverId }: { serverId: string }) {
  const { data, isLoading } = useQuery({ queryKey: ['prompts', serverId], queryFn: () => api.servers.prompts(serverId) })
  if (isLoading) return <Center><Spinner className="size-6" /></Center>
  if (!data?.length) return <Center>This server exposes no prompts.</Center>
  return <div className="space-y-2">{data.map((p) => <PromptRow key={p.name} serverId={serverId} prompt={p} />)}</div>
}

function PromptRow({ serverId, prompt }: { serverId: string; prompt: Prompt }) {
  const [open, setOpen] = useState(false)
  const [args, setArgs] = useState<Record<string, string>>({})
  const [out, setOut] = useState<{ role: string; text: string }[] | null>(null)
  const [busy, setBusy] = useState(false)
  return (
    <div className="rounded-2xl border border-line">
      <button onClick={() => setOpen(!open)} className="flex w-full items-start gap-3 px-4 py-3 text-left">
        <MessageSquareText className="mt-0.5 size-4 shrink-0 text-accent" />
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm font-medium">{prompt.name}</div>
          {prompt.description && <div className="text-[13px] text-muted">{prompt.description}</div>}
        </div>
      </button>
      {open && (
        <div className="space-y-3 border-t border-line p-4">
          {prompt.arguments.map((a) => (
            <Input key={a.name} placeholder={`${a.name}${a.required ? ' *' : ''}${a.description ? ` — ${a.description}` : ''}`} value={args[a.name] ?? ''} onChange={(e) => setArgs({ ...args, [a.name]: e.target.value })} />
          ))}
          <Button size="sm" variant="primary" loading={busy} onClick={async () => {
            setBusy(true)
            try { setOut(await api.servers.getPrompt(serverId, prompt.name, args)) } finally { setBusy(false) }
          }}>Get prompt</Button>
          {out?.map((m, i) => (
            <div key={i} className="rounded-xl bg-elev-2 p-3 text-sm">
              <div className="mb-1 text-xs font-semibold text-muted uppercase">{m.role}</div>
              <div className="whitespace-pre-wrap">{m.text}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function LogsTab({ serverId }: { serverId: string }) {
  const { data } = useQuery({ queryKey: ['logs', serverId], queryFn: () => api.servers.logs(serverId), refetchInterval: 2000 })
  const end = useRef<HTMLDivElement>(null)
  useEffect(() => end.current?.scrollIntoView({ block: 'end' }), [data?.length])
  if (!data?.length) return <Center>No log output yet.</Center>
  const tone = (l: string) => (l === 'error' ? 'text-err' : l === 'stderr' ? 'text-warn' : l === 'warning' ? 'text-warn' : 'text-muted')
  return (
    <div className="rounded-xl border border-line bg-code p-3 font-mono text-xs leading-relaxed">
      {data.map((l, i) => (
        <div key={i} className="flex gap-3">
          <span className="shrink-0 text-muted/60 tabular-nums">{new Date(l.at).toLocaleTimeString()}</span>
          <span className={cn('w-12 shrink-0 uppercase', tone(l.level))}>{l.level}</span>
          <span className="break-all whitespace-pre-wrap text-fg-2">{l.message}</span>
        </div>
      ))}
      <div ref={end} />
    </div>
  )
}

function Center({ children }: { children: React.ReactNode }) {
  return <div className="flex flex-col items-center justify-center py-16 text-sm text-muted">{children}</div>
}
