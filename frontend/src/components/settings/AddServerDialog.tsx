import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowLeft, Braces, ExternalLink, Globe2, LayoutGrid, Search, SquarePen } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { useCatalog, useServers } from '@/lib/queries'
import type { CatalogEntry } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { Spinner } from '@/components/ui/Spinner'
import { ServerForm } from './ServerForm'
import { RegistryTab } from './RegistryTab'

type Tab = 'catalog' | 'registry' | 'custom' | 'import'

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: 'catalog', label: 'Catalog', icon: LayoutGrid },
  { id: 'registry', label: 'Public registry', icon: Globe2 },
  { id: 'custom', label: 'Custom', icon: SquarePen },
  { id: 'import', label: 'Import JSON', icon: Braces },
]

export function AddServerDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [tab, setTab] = useState<Tab>('catalog')
  return (
    <Modal open={open} onClose={onClose} title="Add MCP server" description="Give the assistant access to a new data source." className="h-[88dvh] sm:h-[80vh] sm:max-w-3xl">
      <div className="flex shrink-0 gap-1 overflow-x-auto border-b border-line px-6">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              'relative flex items-center gap-2 px-3 py-2.5 text-sm font-medium transition',
              tab === t.id ? 'text-fg' : 'text-muted hover:text-fg',
            )}
          >
            <t.icon className="size-4" /> {t.label}
            {tab === t.id && <motion.span layoutId="add-tab" className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-gradient-accent" />}
          </button>
        ))}
      </div>
      {tab === 'catalog' && <CatalogTab onDone={onClose} />}
      {tab === 'registry' && <RegistryTab onDone={onClose} />}
      {tab === 'custom' && <ServerForm onDone={onClose} onCancel={onClose} />}
      {tab === 'import' && <ImportTab onDone={onClose} />}
    </Modal>
  )
}

function CatalogTab({ onDone }: { onDone: () => void }) {
  const { data: catalog, isLoading } = useCatalog()
  const { data: servers = [] } = useServers()
  const [q, setQ] = useState('')
  const [cat, setCat] = useState('All')
  const [picked, setPicked] = useState<CatalogEntry | null>(null)

  const categories = useMemo(() => ['All', ...Array.from(new Set(catalog?.map((c) => c.category) ?? []))], [catalog])
  const list = (catalog ?? []).filter(
    (c) => (cat === 'All' || c.category === cat) && (c.name + c.description).toLowerCase().includes(q.toLowerCase()),
  )
  const installed = new Set(servers.map((s) => s.catalogId).filter(Boolean))

  return (
    <div className="relative min-h-0 flex-1 overflow-hidden">
      <AnimatePresence mode="wait" initial={false}>
        {picked ? (
          <motion.div key="form" className="absolute inset-0 flex flex-col" initial={{ x: 40, opacity: 0 }} animate={{ x: 0, opacity: 1 }} exit={{ x: 40, opacity: 0 }}>
            <InstallForm entry={picked} onBack={() => setPicked(null)} onDone={onDone} />
          </motion.div>
        ) : (
          <motion.div key="grid" className="absolute inset-0 flex flex-col" initial={{ x: -40, opacity: 0 }} animate={{ x: 0, opacity: 1 }} exit={{ x: -40, opacity: 0 }}>
            <div className="flex shrink-0 flex-wrap items-center gap-2 px-6 py-3">
              <div className="relative min-w-48 flex-1">
                <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
                <Input className="pl-9" placeholder="Search servers…" value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
              </div>
              <div className="flex flex-wrap gap-1">
                {categories.map((c) => (
                  <button
                    key={c}
                    onClick={() => setCat(c)}
                    className={cn('rounded-full px-3 py-1 text-xs font-medium transition', cat === c ? 'bg-fg text-bg' : 'bg-elev-2 text-fg-2 hover:text-fg')}
                  >
                    {c}
                  </button>
                ))}
              </div>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">
              {isLoading ? (
                <div className="flex justify-center py-12 text-muted"><Spinner className="size-6" /></div>
              ) : (
                <div className="grid gap-2.5 sm:grid-cols-2">
                  {list.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => setPicked(c)}
                      className="group flex items-start gap-3 rounded-2xl border border-line bg-elev p-3.5 text-left transition hover:-translate-y-0.5 hover:border-line-strong hover:shadow-soft"
                    >
                      <ServerIcon name={c.name} icon={c.icon} color={c.color} size="md" />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold">{c.name}</span>
                          {installed.has(c.id) && <Badge tone="ok">Installed</Badge>}
                        </div>
                        <p className="mt-0.5 line-clamp-2 text-[13px] text-muted">{c.description}</p>
                        <div className="mt-2 flex gap-1.5">
                          <Badge>{c.category}</Badge>
                          <Badge tone={c.runtime.startsWith('Remote') ? 'accent' : 'neutral'}>{c.runtime}</Badge>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function InstallForm({ entry, onBack, onDone }: { entry: CatalogEntry; onBack: () => void; onDone: () => void }) {
  const qc = useQueryClient()
  const [values, setValues] = useState<Record<string, string>>({})
  const [name, setName] = useState(entry.name)
  const [busy, setBusy] = useState(false)
  const missing = entry.params.some((p) => p.required && !values[p.key]?.trim())

  const install = async () => {
    setBusy(true)
    try {
      const s = await api.catalog.install(entry.id, values, name)
      qc.invalidateQueries({ queryKey: ['servers'] })
      qc.invalidateQueries({ queryKey: ['toolsets'] })
      toast.success(`${s.name} installed`, { description: 'Connecting — tools will appear in a moment.' })
      onDone()
    } catch (e) {
      toast.error('Install failed', { description: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }

  const preview = entry.transport === 'STDIO' ? [entry.command, ...entry.args].join(' ') : entry.url

  return (
    <>
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-4">
        <button onClick={onBack} className="flex items-center gap-1.5 text-sm text-muted hover:text-fg">
          <ArrowLeft className="size-4" /> Catalog
        </button>
        <div className="flex items-start gap-4">
          <ServerIcon name={entry.name} icon={entry.icon} color={entry.color} size="lg" />
          <div className="min-w-0">
            <h3 className="text-lg font-semibold">{entry.name}</h3>
            <p className="text-sm text-muted">{entry.description}</p>
            {entry.docsUrl && (
              <a href={entry.docsUrl} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-xs text-accent hover:underline">
                Documentation <ExternalLink className="size-3" />
              </a>
            )}
          </div>
        </div>
        <code className="block overflow-x-auto rounded-xl border border-line bg-code px-3 py-2.5 font-mono text-xs whitespace-nowrap text-fg-2">{preview}</code>
        <Field label="Display name">
          <Input value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        {entry.params.map((p) => (
          <Field key={p.key} label={p.label} required={p.required} hint={p.help}>
            <Input
              type={p.secret ? 'password' : 'text'}
              className="font-mono"
              placeholder={p.placeholder}
              value={values[p.key] ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, [p.key]: e.target.value }))}
            />
          </Field>
        ))}
        {entry.runtime.includes('OAuth') && (
          <p className="rounded-xl bg-accent-soft px-3 py-2.5 text-xs text-fg-2">Uses <code className="font-mono">mcp-remote</code>: on first connect a browser window opens on the machine running the MCP backend to sign in.</p>
        )}
        {entry.runtime.includes('uv') && (
          <p className="rounded-xl bg-elev-2 px-3 py-2.5 text-xs text-muted">Requires <code className="font-mono">uv</code> on the backend host (<code className="font-mono">brew install uv</code>).</p>
        )}
        {entry.runtime === 'Node.js' && (
          <p className="rounded-xl bg-elev-2 px-3 py-2.5 text-xs text-muted">Runs via <code className="font-mono">npx</code> — the first start may take a few seconds while the package downloads.</p>
        )}
      </div>
      <div className="flex shrink-0 justify-end gap-2 border-t border-line px-6 py-4">
        <Button variant="ghost" onClick={onBack}>Back</Button>
        <Button variant="primary" onClick={install} loading={busy} disabled={missing}>Install & connect</Button>
      </div>
    </>
  )
}

const EXAMPLE = `{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/me/Documents"]
    },
    "deepwiki": {
      "type": "http",
      "url": "https://mcp.deepwiki.com/mcp"
    }
  }
}`

function ImportTab({ onDone }: { onDone: () => void }) {
  const qc = useQueryClient()
  const [json, setJson] = useState('')
  const [busy, setBusy] = useState(false)
  const run = async () => {
    setBusy(true)
    try {
      const r = await api.importJson(json)
      qc.invalidateQueries({ queryKey: ['servers'] })
      qc.invalidateQueries({ queryKey: ['toolsets'] })
      if (r.created.length) toast.success(`Imported ${r.created.length} server${r.created.length > 1 ? 's' : ''}`)
      if (r.skipped.length) toast.warning(`Skipped ${r.skipped.length}`, { description: r.skipped.join('\n') })
      if (r.created.length) onDone()
    } catch (e) {
      toast.error('Import failed', { description: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-6 py-4">
        <p className="text-sm text-muted">
          Paste the <code className="rounded bg-elev-2 px-1 font-mono text-xs">mcpServers</code> block from Claude Desktop, Cursor, Windsurf or VS Code.
        </p>
        <Textarea className="min-h-[300px] font-mono text-[13px]" value={json} onChange={(e) => setJson(e.target.value)} placeholder={EXAMPLE} spellCheck={false} />
        <button className="text-xs font-medium text-accent hover:underline" onClick={() => setJson(EXAMPLE)}>Use example</button>
      </div>
      <div className="flex shrink-0 justify-end gap-2 border-t border-line px-6 py-4">
        <Button variant="primary" onClick={run} loading={busy} disabled={!json.trim()}>Import servers</Button>
      </div>
    </>
  )
}
