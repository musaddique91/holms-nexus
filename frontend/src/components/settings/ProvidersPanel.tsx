import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, CircleAlert, Pencil, Plus, RefreshCw, Trash2, Zap } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { useProviders } from '@/lib/queries'
import type { Provider, ProviderType } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Field, Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Switch } from '@/components/ui/Switch'
import { confirm } from '@/components/ui/Confirm'
import { PanelHeader } from './SettingsDialog'
import { ProviderGlyph, providerLabel } from './ProviderGlyph'

const DEFAULTS: Record<ProviderType, { name: string; model: string; baseUrl: string; keyPlaceholder: string; tint: string }> = {
  ANTHROPIC: { name: 'Anthropic', model: 'claude-sonnet-5-5', baseUrl: '', keyPlaceholder: 'sk-ant-…', tint: '#d97757' },
  OPENAI: { name: 'OpenAI', model: 'gpt-5', baseUrl: '', keyPlaceholder: 'sk-…', tint: '#10a37f' },
  OLLAMA: { name: 'Ollama', model: 'qwen3', baseUrl: 'http://localhost:11434', keyPlaceholder: '', tint: '#a78bfa' },
}

export function ProvidersPanel() {
  const { data: providers = [] } = useProviders()
  const [editing, setEditing] = useState<Provider | 'new' | null>(null)
  return (
    <div>
      <PanelHeader
        title="Models"
        description="Bring your own keys. Keys are stored on your server and never sent back to the browser."
        actions={<Button variant="primary" size="sm" onClick={() => setEditing('new')}><Plus className="size-4" /> Add provider</Button>}
      />
      <div className="grid gap-3 p-6 lg:grid-cols-2">
        {providers.map((p) => <ProviderCard key={p.id} p={p} onEdit={() => setEditing(p)} />)}
      </div>
      <ProviderDialog provider={editing} onClose={() => setEditing(null)} />
    </div>
  )
}

function ProviderCard({ p, onEdit }: { p: Provider; onEdit: () => void }) {
  const qc = useQueryClient()
  const [test, setTest] = useState<{ ok: boolean; message: string; latencyMs: number } | null>(null)
  const testM = useMutation({ mutationFn: () => api.providers.test(p.id), onSuccess: setTest })
  const refresh = useMutation({
    mutationFn: () => api.providers.refreshModels(p.id),
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ['providers'] }); toast.success(`Found ${r.models.length} models`) },
    onError: (e) => toast.error((e as Error).message),
  })
  const remove = async () => {
    if (!(await confirm({ title: `Remove ${p.name}?`, confirmLabel: 'Remove', danger: true }))) return
    await api.providers.remove(p.id)
    qc.invalidateQueries({ queryKey: ['providers'] })
  }
  const tint = DEFAULTS[p.type].tint
  return (
    <div className="flex flex-col rounded-2xl border border-line bg-elev p-4">
      <div className="flex items-start gap-3">
        <span className="flex size-12 items-center justify-center rounded-2xl border" style={{ color: tint, background: `color-mix(in oklab, ${tint} 12%, transparent)`, borderColor: `color-mix(in oklab, ${tint} 25%, transparent)` }}>
          <ProviderGlyph type={p.type} className="size-6" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold">{p.name}</h3>
            {p.ready ? <Badge tone="ok">Ready</Badge> : <Badge tone="warn">Needs API key</Badge>}
            {!p.enabled && <Badge>Disabled</Badge>}
          </div>
          <div className="text-xs text-muted">{providerLabel[p.type]}{p.baseUrl ? ` · ${p.baseUrl}` : ''}</div>
        </div>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div><dt className="text-xs text-muted">Default model</dt><dd className="truncate font-mono text-[13px]">{p.defaultModel}</dd></div>
        <div><dt className="text-xs text-muted">API key</dt><dd className="font-mono text-[13px]">{p.apiKeyHint ?? (p.type === 'OLLAMA' ? 'not needed' : '—')}</dd></div>
      </dl>
      <div className="mt-3 flex flex-wrap gap-1">
        {p.models.slice(0, 6).map((m) => <Badge key={m} className="font-mono">{m}</Badge>)}
        {p.models.length > 6 && <Badge>+{p.models.length - 6}</Badge>}
      </div>
      {test && (
        <div className={cn('mt-3 flex items-start gap-2 rounded-xl px-3 py-2 text-xs', test.ok ? 'bg-ok/10 text-ok' : 'bg-err/10 text-err')}>
          {test.ok ? <CheckCircle2 className="size-4 shrink-0" /> : <CircleAlert className="size-4 shrink-0" />}
          <span className="break-words">{test.message} · {test.latencyMs}ms</span>
        </div>
      )}
      <div className="mt-auto flex items-center gap-1 pt-4">
        <Button size="sm" variant="outline" onClick={() => testM.mutate()} loading={testM.isPending} disabled={!p.ready}>
          {!testM.isPending && <Zap className="size-3.5" />} Test
        </Button>
        <Button size="sm" variant="ghost" onClick={() => refresh.mutate()} disabled={!p.ready || refresh.isPending}>
          <RefreshCw className={cn('size-3.5', refresh.isPending && 'animate-spin')} /> Fetch models
        </Button>
        <div className="ml-auto flex">
          <Button size="icon-sm" variant="ghost" onClick={onEdit} aria-label="Edit"><Pencil className="size-3.5" /></Button>
          <Button size="icon-sm" variant="ghost" onClick={remove} aria-label="Remove" className="hover:text-err"><Trash2 className="size-3.5" /></Button>
        </div>
      </div>
    </div>
  )
}

function ProviderDialog({ provider, onClose }: { provider: Provider | 'new' | null; onClose: () => void }) {
  const qc = useQueryClient()
  const existing = provider && provider !== 'new' ? provider : null
  const [type, setType] = useState<ProviderType>('ANTHROPIC')
  const [name, setName] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [models, setModels] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!provider) return
    const t = existing?.type ?? 'ANTHROPIC'
    setType(t)
    setName(existing?.name ?? DEFAULTS[t].name)
    setApiKey('')
    setBaseUrl(existing?.baseUrl ?? DEFAULTS[t].baseUrl)
    setModel(existing?.defaultModel ?? DEFAULTS[t].model)
    setModels(existing?.models.join(', ') ?? '')
    setEnabled(existing?.enabled ?? true)
  }, [provider]) // eslint-disable-line react-hooks/exhaustive-deps

  const pickType = (t: ProviderType) => {
    setType(t)
    if (!existing) {
      setName(DEFAULTS[t].name)
      setModel(DEFAULTS[t].model)
      setBaseUrl(DEFAULTS[t].baseUrl)
    }
  }

  const save = async () => {
    setBusy(true)
    try {
      const body = {
        name, type, apiKey: apiKey || undefined, baseUrl: baseUrl || null, defaultModel: model, enabled,
        models: models ? models.split(',').map((m) => m.trim()).filter(Boolean) : undefined,
      }
      if (existing) await api.providers.update(existing.id, body)
      else await api.providers.create(body)
      qc.invalidateQueries({ queryKey: ['providers'] })
      toast.success('Provider saved')
      onClose()
    } catch (e) {
      toast.error('Could not save', { description: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={!!provider} onClose={onClose} title={existing ? `Edit ${existing.name}` : 'Add model provider'} className="sm:max-w-xl">
      <div className="space-y-4 overflow-y-auto px-6 py-3">
        <div className="grid grid-cols-3 gap-2">
          {(['ANTHROPIC', 'OPENAI', 'OLLAMA'] as ProviderType[]).map((t) => (
            <button
              key={t}
              onClick={() => pickType(t)}
              className={cn('flex flex-col items-center gap-1.5 rounded-xl border p-3 text-sm transition', type === t ? 'border-accent/60 bg-accent-soft ring-4 ring-accent/10' : 'border-line hover:border-line-strong')}
            >
              <ProviderGlyph type={t} className="size-5" />
              {providerLabel[t]}
            </button>
          ))}
        </div>
        <Field label="Name"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        {type !== 'OLLAMA' && (
          <Field label="API key" hint={existing?.hasApiKey ? `Current: ${existing.apiKeyHint} — leave blank to keep it.` : undefined}>
            <Input type="password" className="font-mono" placeholder={DEFAULTS[type].keyPlaceholder} value={apiKey} onChange={(e) => setApiKey(e.target.value)} autoComplete="off" />
          </Field>
        )}
        <Field label="Base URL" hint={type === 'OPENAI' ? 'Optional — any OpenAI-compatible endpoint (Groq, Together, OpenRouter, LM Studio…), e.g. https://openrouter.ai/api/v1' : type === 'OLLAMA' ? 'Where Ollama is running' : 'Optional — leave blank for api.anthropic.com'}>
          <Input className="font-mono" value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} placeholder={type === 'OLLAMA' ? 'http://localhost:11434' : 'default'} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Default model"><Input className="font-mono" value={model} onChange={(e) => setModel(e.target.value)} /></Field>
          <Field label="Models (comma separated)" hint="Or use “Fetch models” later.">
            <Input className="font-mono" value={models} onChange={(e) => setModels(e.target.value)} placeholder="optional" />
          </Field>
        </div>
        <div className="flex items-center justify-between rounded-xl border border-line px-3 py-2.5">
          <span className="text-sm">Enabled</span>
          <Switch checked={enabled} onChange={setEnabled} />
        </div>
      </div>
      <div className="flex justify-end gap-2 border-t border-line px-6 py-4">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant="primary" onClick={save} loading={busy} disabled={!name.trim() || !model.trim()}>Save</Button>
      </div>
    </Modal>
  )
}
