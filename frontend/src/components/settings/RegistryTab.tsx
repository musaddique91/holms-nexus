import { useEffect, useState } from 'react'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowLeft, Box, ExternalLink, Globe, Package, Search, Terminal } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import type { RegistryOption, RegistryServer } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { Spinner } from '@/components/ui/Spinner'

const KIND: Record<RegistryOption['kind'], { icon: React.ElementType; tone: 'accent' | 'neutral' | 'warn' }> = {
  remote: { icon: Globe, tone: 'accent' },
  npm: { icon: Package, tone: 'neutral' },
  pypi: { icon: Terminal, tone: 'neutral' },
  oci: { icon: Box, tone: 'warn' },
}

function useDebounced<T>(value: T, ms = 350) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

/** Browse & install from the official MCP Registry (registry.modelcontextprotocol.io). */
export function RegistryTab({ onDone }: { onDone: () => void }) {
  const [q, setQ] = useState('')
  const query = useDebounced(q.trim())
  const [picked, setPicked] = useState<RegistryServer | null>(null)

  const results = useInfiniteQuery({
    queryKey: ['registry', query],
    queryFn: ({ pageParam }) => api.registry.search(query, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? null,
    staleTime: 5 * 60_000,
  })
  const servers = results.data?.pages.flatMap((p) => p.servers) ?? []

  return (
    <div className="relative min-h-0 flex-1 overflow-hidden">
      <AnimatePresence mode="wait" initial={false}>
        {picked ? (
          <motion.div key="form" className="absolute inset-0 flex flex-col" initial={{ x: 40, opacity: 0 }} animate={{ x: 0, opacity: 1 }} exit={{ x: 40, opacity: 0 }}>
            <RegistryInstall server={picked} onBack={() => setPicked(null)} onDone={onDone} />
          </motion.div>
        ) : (
          <motion.div key="list" className="absolute inset-0 flex flex-col" initial={{ x: -40, opacity: 0 }} animate={{ x: 0, opacity: 1 }} exit={{ x: -40, opacity: 0 }}>
            <div className="shrink-0 space-y-2 px-6 py-3">
              <div className="relative">
                <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
                <Input className="pl-9" placeholder="Search thousands of public MCP servers — e.g. gmail, jira, snowflake…" value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
                {results.isFetching && <Spinner className="absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted" />}
              </div>
              <p className="text-xs text-muted">
                Live from the official <a className="text-accent hover:underline" href="https://registry.modelcontextprotocol.io" target="_blank" rel="noreferrer">MCP Registry</a>.
                Community servers aren't reviewed by holms-nexus — check the source before adding credentials.
              </p>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">
              {results.isLoading ? (
                <div className="flex flex-col items-center gap-3 py-16 text-sm text-muted"><Spinner className="size-6" /> Searching the registry…</div>
              ) : results.isError ? (
                <div className="py-12 text-center text-sm text-err">{(results.error as Error).message}</div>
              ) : servers.length === 0 ? (
                <div className="py-12 text-center text-sm text-muted">No servers match “{query}”.</div>
              ) : (
                <div className="space-y-2">
                  {servers.map((s) => (
                    <button
                      key={s.name}
                      onClick={() => setPicked(s)}
                      className="group flex w-full items-start gap-3 rounded-2xl border border-line bg-elev p-3.5 text-left transition hover:border-line-strong hover:shadow-soft"
                    >
                      {s.iconUrl ? (
                        <img src={s.iconUrl} alt="" className="size-9 shrink-0 rounded-xl border border-line object-cover" loading="lazy" />
                      ) : (
                        <ServerIcon name={s.title} size="md" />
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold">{s.title}</span>
                          {s.version && <span className="font-mono text-[11px] text-muted">v{s.version}</span>}
                        </div>
                        <div className="truncate font-mono text-[11px] text-muted">{s.name}</div>
                        {s.description && <p className="mt-1 line-clamp-2 text-[13px] text-fg-2">{s.description}</p>}
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {s.options.map((o) => {
                            const K = KIND[o.kind]
                            return <Badge key={o.id} tone={K.tone}><K.icon className="size-3" />{o.label}</Badge>
                          })}
                        </div>
                      </div>
                    </button>
                  ))}
                  {results.hasNextPage && (
                    <Button variant="outline" className="w-full" onClick={() => results.fetchNextPage()} loading={results.isFetchingNextPage}>
                      Load more
                    </Button>
                  )}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function RegistryInstall({ server, onBack, onDone }: { server: RegistryServer; onBack: () => void; onDone: () => void }) {
  const qc = useQueryClient()
  const [optionId, setOptionId] = useState(server.options[0]?.id)
  const [values, setValues] = useState<Record<string, string>>({})
  const [name, setName] = useState(server.title)
  const [busy, setBusy] = useState(false)
  const option = server.options.find((o) => o.id === optionId) ?? server.options[0]
  const missing = option.fields.some((f) => f.required && !values[f.key]?.trim() && !f.defaultValue)

  const install = async () => {
    setBusy(true)
    try {
      const s = await api.registry.install(server.name, option.id, values, name)
      qc.invalidateQueries({ queryKey: ['servers'] })
      qc.invalidateQueries({ queryKey: ['toolsets'] })
      toast.success(`${s.name} added`, { description: 'Connecting — tools appear in a moment.' })
      onDone()
    } catch (e) {
      toast.error('Install failed', { description: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-4">
        <button onClick={onBack} className="flex items-center gap-1.5 text-sm text-muted hover:text-fg"><ArrowLeft className="size-4" /> Registry</button>
        <div className="flex items-start gap-4">
          {server.iconUrl ? <img src={server.iconUrl} alt="" className="size-12 rounded-2xl border border-line" /> : <ServerIcon name={server.title} size="lg" />}
          <div className="min-w-0">
            <h3 className="text-lg font-semibold">{server.title}</h3>
            <p className="font-mono text-xs text-muted">{server.name}{server.version ? ` · v${server.version}` : ''}</p>
            {server.description && <p className="mt-1 text-sm text-fg-2">{server.description}</p>}
            <div className="mt-1 flex gap-3">
              {server.repositoryUrl && <a href={server.repositoryUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-accent hover:underline">Source <ExternalLink className="size-3" /></a>}
              {server.websiteUrl && <a href={server.websiteUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-accent hover:underline">Website <ExternalLink className="size-3" /></a>}
            </div>
          </div>
        </div>

        {server.options.length > 1 && (
          <Field label="How to run it">
            <div className="grid gap-2 sm:grid-cols-2">
              {server.options.map((o) => {
                const K = KIND[o.kind]
                return (
                  <button key={o.id} onClick={() => setOptionId(o.id)}
                    className={cn('flex items-center gap-2 rounded-xl border p-3 text-left text-sm transition', o.id === option.id ? 'border-accent/60 bg-accent-soft ring-4 ring-accent/10' : 'border-line hover:border-line-strong')}>
                    <K.icon className={cn('size-4', o.id === option.id ? 'text-accent' : 'text-muted')} /> {o.label}
                  </button>
                )
              })}
            </div>
          </Field>
        )}
        <code className="block overflow-x-auto rounded-xl border border-line bg-code px-3 py-2.5 font-mono text-xs whitespace-nowrap text-fg-2">{option.preview}</code>

        <Field label="Display name"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        {option.fields.map((f) => (
          <Field key={f.key} label={<span className="font-mono">{f.label}</span>} required={f.required} hint={f.description}>
            <Input
              type={f.secret ? 'password' : 'text'}
              className="font-mono"
              placeholder={f.defaultValue ?? (f.key === 'header:Authorization' ? 'Bearer …' : '')}
              value={values[f.key] ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
              autoComplete="off"
            />
          </Field>
        ))}
        {option.kind === 'pypi' && <p className="rounded-xl bg-elev-2 px-3 py-2.5 text-xs text-muted">Runs with <code className="font-mono">uvx</code> — install uv on the backend host (<code className="font-mono">brew install uv</code>).</p>}
        {option.kind === 'oci' && <p className="rounded-xl bg-elev-2 px-3 py-2.5 text-xs text-muted">Runs as a Docker container on the backend host each time it connects.</p>}
      </div>
      <div className="flex shrink-0 justify-end gap-2 border-t border-line px-6 py-4">
        <Button variant="ghost" onClick={onBack}>Back</Button>
        <Button variant="primary" onClick={install} loading={busy} disabled={missing || !name.trim()}>Add & connect</Button>
      </div>
    </>
  )
}
