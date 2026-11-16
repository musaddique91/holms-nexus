import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, CircleAlert, FlaskConical } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import type { McpServer, ServerRequest, Transport } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Input'
import { KeyValueEditor } from '@/components/ui/KeyValueEditor'
import { Modal } from '@/components/ui/Modal'
import { transportMeta } from './ServersPanel'

interface FormState {
  name: string
  description: string
  transport: Transport
  command: string
  args: string
  env: [string, string][]
  url: string
  headers: [string, string][]
}

const empty: FormState = { name: '', description: '', transport: 'STDIO', command: '', args: '', env: [], url: '', headers: [] }

function fromServer(s: McpServer): FormState {
  return {
    name: s.name,
    description: s.description ?? '',
    transport: s.transport,
    command: s.command ?? '',
    args: s.args.join('\n'),
    env: Object.entries(s.env),
    url: s.url ?? '',
    headers: Object.entries(s.headers),
  }
}

/** Splits args on new lines; a single line is split shell-style on spaces (respecting quotes). */
function parseArgs(raw: string): string[] {
  const lines = raw.split('\n').map((l) => l.trim()).filter(Boolean)
  if (lines.length !== 1) return lines
  return lines[0].match(/"[^"]*"|'[^']*'|\S+/g)?.map((a) => a.replace(/^["']|["']$/g, '')) ?? []
}

function toRequest(f: FormState, base?: McpServer): ServerRequest {
  return {
    name: f.name,
    description: f.description || null,
    transport: f.transport,
    command: f.transport === 'STDIO' ? f.command : null,
    args: f.transport === 'STDIO' ? parseArgs(f.args) : [],
    env: f.transport === 'STDIO' ? Object.fromEntries(f.env.filter(([k]) => k.trim())) : {},
    url: f.transport !== 'STDIO' ? f.url : null,
    headers: f.transport !== 'STDIO' ? Object.fromEntries(f.headers.filter(([k]) => k.trim())) : {},
    enabled: base?.enabled ?? true,
    icon: base?.icon,
    color: base?.color,
    catalogId: base?.catalogId,
  }
}

export function ServerForm({ server, onDone, onCancel }: { server?: McpServer | null; onDone: () => void; onCancel?: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<FormState>(server ? fromServer(server) : empty)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [test, setTest] = useState<McpServer | null>(null)

  useEffect(() => {
    setF(server ? fromServer(server) : empty)
    setTest(null)
  }, [server])

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((p) => ({ ...p, [k]: v }))
  const valid = f.name.trim() && (f.transport === 'STDIO' ? f.command.trim() : /^https?:\/\//.test(f.url.trim()))

  const runTest = async () => {
    setTesting(true)
    setTest(null)
    try {
      setTest(await api.servers.test(toRequest({ ...f, name: f.name || 'test' }, server ?? undefined)))
    } catch (e) {
      toast.error('Test failed', { description: (e as Error).message })
    } finally {
      setTesting(false)
    }
  }

  const save = async () => {
    setSaving(true)
    try {
      const req = toRequest(f, server ?? undefined)
      const saved = server ? await api.servers.update(server.id, req) : await api.servers.create(req)
      qc.invalidateQueries({ queryKey: ['servers'] })
      qc.invalidateQueries({ queryKey: ['toolsets'] })
      toast.success(server ? `${saved.name} updated` : `${saved.name} added`, { description: 'Connecting…' })
      onDone()
    } catch (e) {
      toast.error('Could not save server', { description: (e as Error).message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Name" required>
            <Input value={f.name} onChange={(e) => set('name', e.target.value)} placeholder="e.g. Analytics DB" autoFocus={!server} />
          </Field>
          <Field label="Description">
            <Input value={f.description} onChange={(e) => set('description', e.target.value)} placeholder="What data does it expose?" />
          </Field>
        </div>

        <Field label="Transport">
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(transportMeta) as Transport[]).map((t) => {
              const M = transportMeta[t]
              const on = f.transport === t
              return (
                <button
                  key={t}
                  type="button"
                  onClick={() => set('transport', t)}
                  className={cn(
                    'flex flex-col items-start gap-1 rounded-xl border p-3 text-left transition',
                    on ? 'border-accent/60 bg-accent-soft ring-4 ring-accent/10' : 'border-line hover:border-line-strong',
                  )}
                >
                  <M.icon className={cn('size-4', on ? 'text-accent' : 'text-muted')} />
                  <span className="text-sm font-medium">{t === 'STDIO' ? 'Local command' : t === 'SSE' ? 'SSE (legacy)' : 'Streamable HTTP'}</span>
                  <span className="text-xs text-muted">{t === 'STDIO' ? 'npx, uvx, docker…' : t === 'SSE' ? 'Older remote servers' : 'Remote servers'}</span>
                </button>
              )
            })}
          </div>
        </Field>

        {f.transport === 'STDIO' ? (
          <>
            <Field label="Command" required hint="The executable to launch, e.g. npx, uvx, node, python, docker.">
              <Input className="font-mono" value={f.command} onChange={(e) => set('command', e.target.value)} placeholder="npx" />
            </Field>
            <Field label="Arguments" hint="One per line — or a single line split on spaces.">
              <Textarea className="min-h-[84px] font-mono text-[13px]" value={f.args} onChange={(e) => set('args', e.target.value)} placeholder={'-y\n@modelcontextprotocol/server-filesystem\n/Users/me/Documents'} />
            </Field>
            <Field label="Environment variables">
              <KeyValueEditor value={f.env} onChange={(v) => set('env', v)} keyPlaceholder="API_KEY" addLabel="Add variable" />
            </Field>
          </>
        ) : (
          <>
            <Field label="Server URL" required>
              <Input className="font-mono" value={f.url} onChange={(e) => set('url', e.target.value)} placeholder={f.transport === 'SSE' ? 'https://example.com/sse' : 'https://example.com/mcp'} />
            </Field>
            <Field label="HTTP headers" hint="e.g. Authorization: Bearer <token>">
              <KeyValueEditor value={f.headers} onChange={(v) => set('headers', v)} keyPlaceholder="Authorization" valuePlaceholder="Bearer …" addLabel="Add header" />
            </Field>
          </>
        )}

        {test && (
          <div className={cn('flex items-start gap-3 rounded-xl border p-3.5 text-sm', test.status === 'CONNECTED' ? 'border-ok/25 bg-ok/5' : 'border-err/25 bg-err/5')}>
            {test.status === 'CONNECTED' ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-ok" /> : <CircleAlert className="mt-0.5 size-4 shrink-0 text-err" />}
            <div className="min-w-0">
              {test.status === 'CONNECTED' ? (
                <>
                  <div className="font-medium text-ok">Connected to {test.serverName} {test.serverVersion}</div>
                  <div className="text-muted">{test.toolCount} tools · {test.resourceCount} resources · {test.promptCount} prompts</div>
                </>
              ) : (
                <>
                  <div className="font-medium text-err">Connection failed</div>
                  <div className="break-words text-fg-2">{test.error}</div>
                </>
              )}
            </div>
          </div>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-2 border-t border-line px-6 py-4">
        <Button variant="outline" onClick={runTest} loading={testing} disabled={!valid}>
          {!testing && <FlaskConical className="size-4" />} Test connection
        </Button>
        <div className="ml-auto flex gap-2">
          {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
          <Button variant="primary" onClick={save} loading={saving} disabled={!valid}>
            {server ? 'Save changes' : 'Add server'}
          </Button>
        </div>
      </div>
    </div>
  )
}

export function ServerFormDialog({ server, open, onClose }: { server: McpServer | null; open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title={`Edit ${server?.name ?? 'server'}`} description="Changes reconnect the server." className="sm:max-w-2xl">
      <ServerForm server={server} onDone={onClose} onCancel={onClose} />
    </Modal>
  )
}
