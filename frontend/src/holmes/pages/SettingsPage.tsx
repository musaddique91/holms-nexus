import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bell, Bot, CheckCircle2, ChevronDown, KeyRound, Loader2, RefreshCw, Save, Settings2, XCircle, Zap } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useUi } from '@/store/ui'
import { api } from '../api/client'
import type { LlmSettings, LlmSettingsUpdate, LlmTestResult, SharedProvider } from '../api/types'
import { ErrorNote, PageHeader } from '../components/Page'
import { ListSkeleton } from '../components/Skeleton'
import { timeAgo } from '../lib/format'
import { NotificationsCard } from './NotificationsCard'

const TABS = [
  { id: 'ai', label: 'AI model', icon: Bot },
  { id: 'notifications', label: 'Notifications', icon: Bell },
] as const

export function SettingsPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'notifications' ? 'notifications' : 'ai'
  return (
    <>
      <PageHeader title="Settings" subtitle="The AI model Intelligence uses for investigations and where it sends alerts" />
      <div className="mb-5 inline-flex rounded-xl border border-line bg-elev-2/70 p-1" role="tablist" aria-label="Settings sections">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setParams(t.id === 'ai' ? {} : { tab: t.id }, { replace: true })}
            className={clsx(
              'flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-sm transition',
              tab === t.id ? 'bg-elev font-medium text-fg shadow-soft' : 'text-muted hover:text-fg',
            )}
          >
            <t.icon className="size-4" /> {t.label}
          </button>
        ))}
      </div>
      {tab === 'ai' ? <ModelTab /> : <NotificationsTab />}
    </>
  )
}

function ModelTab() {
  const settings = useQuery({ queryKey: ['llm-settings'], queryFn: api.llmSettings })
  return (
    <>
      <ErrorNote error={settings.error} />
      {settings.data ? <SharedModelCard saved={settings.data} /> : <div className="card"><ListSkeleton rows={4} /></div>}
    </>
  )
}

function NotificationsTab() {
  const settings = useQuery({ queryKey: ['notification-settings'], queryFn: api.notificationSettings })
  return (
    <>
      <ErrorNote error={settings.error} />
      {settings.data ? <NotificationsCard saved={settings.data} /> : <div className="card"><ListSkeleton rows={4} /></div>}
    </>
  )
}

interface Form {
  providerId: string
  model: string
  fixModel: string
  maxTokens: number
  temperature: number
  maxTokensParam: string
}

const fromSettings = (s: LlmSettings): Form => ({
  providerId: s.providerId ?? '',
  model: s.model,
  fixModel: s.fixModel ?? '',
  maxTokens: s.maxTokens,
  temperature: s.temperature,
  maxTokensParam: s.maxTokensParam,
})

const TYPE_LABEL: Record<SharedProvider['type'], string> = {
  ANTHROPIC: 'Anthropic',
  OPENAI: 'OpenAI-compatible',
  OLLAMA: 'Ollama',
}

/**
 * Intelligence picks a provider and model from the shared Models list (the same list MCP chat uses).
 * Keys and endpoints are managed there, in Settings → Models.
 */
function SharedModelCard({ saved }: { saved: LlmSettings }) {
  const queryClient = useQueryClient()
  const openSettings = useUi((s) => s.openSettings)
  const [form, setForm] = useState<Form>(() => fromSettings(saved))
  const [test, setTest] = useState<LlmTestResult | null>(null)
  useEffect(() => setForm(fromSettings(saved)), [saved])

  const providers = saved.sharedProviders
  const provider = providers.find((p) => p.id === form.providerId)
  const set = <K extends keyof Form>(k: K, v: Form[K]) => {
    setForm((f) => ({ ...f, [k]: v }))
    setTest(null)
  }

  const payload = (): LlmSettingsUpdate => ({
    providerId: form.providerId,
    model: form.model,
    fixModel: form.fixModel,
    maxTokens: Number(form.maxTokens),
    temperature: Number(form.temperature),
    maxTokensParam: form.maxTokensParam,
  })

  const live = useQuery({
    queryKey: ['llm-models', form.providerId],
    queryFn: () => api.llmModels({ providerId: form.providerId }),
    enabled: !!form.providerId,
    staleTime: 60_000,
  })
  const models = useMemo(() => {
    const all = new Set<string>([...(provider?.models ?? []), ...(live.data ?? [])])
    return [...all].filter((m) => !/embed|rerank|guard|safety|reward|parse|tts|whisper/i.test(m)).sort()
  }, [provider, live.data])

  const save = useMutation({
    mutationFn: () => api.saveLlmSettings(payload()),
    onSuccess: (s) => {
      queryClient.setQueryData(['llm-settings'], s)
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success('AI model saved', { description: `${s.providerName ?? s.provider} · ${s.model} — used from the next investigation.` })
    },
    onError: (e) => toast.error('Could not save', { description: e instanceof Error ? e.message : undefined }),
  })
  const runTest = useMutation({
    mutationFn: () => api.testLlm(payload()),
    onMutate: () => setTest(null),
    onSuccess: (r) => setTest(r),
    onError: (e) => setTest({ ok: false, toolCalling: false, latencyMs: 0, model: form.model, detail: e instanceof Error ? e.message : 'Failed' }),
  })

  const dirty = JSON.stringify(form) !== JSON.stringify(fromSettings(saved))

  return (
    <section className="card p-5 md:p-6" aria-labelledby="llm-title">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="llm-title" className="text-base font-semibold text-fg">AI model</h2>
          <p className="mt-0.5 text-sm text-fg-3">
            Chosen from the shared Models list, also used by MCP. Changes apply to the next investigation.{' '}
            {saved.updatedAt && <>Last changed {timeAgo(saved.updatedAt)}{saved.updatedBy ? ` by ${saved.updatedBy}` : ''}.</>}
          </p>
        </div>
        <button type="button" className="btn-secondary" onClick={() => openSettings('models')}>
          <Settings2 className="size-4" /> Manage models
        </button>
      </div>

      {providers.length === 0 ? (
        <div className="rounded-xl border border-dashed border-line px-5 py-10 text-center">
          <KeyRound className="mx-auto size-6 text-fg-3" />
          <p className="mt-3 text-sm font-medium text-fg">No providers yet</p>
          <p className="mt-1 text-sm text-fg-3">Add one under Models (NVIDIA, OpenAI, Anthropic, Ollama…), then choose it here.</p>
          <button type="button" className="btn-primary mt-4" onClick={() => openSettings('models')}>Add a provider</button>
        </div>
      ) : (
        <>
          <fieldset>
            <legend className="label">Provider</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3" role="radiogroup">
              {providers.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  role="radio"
                  aria-checked={form.providerId === p.id}
                  disabled={!p.enabled}
                  onClick={() => {
                    if (p.id === form.providerId) return
                    setForm((f) => ({ ...f, providerId: p.id, model: p.defaultModel, fixModel: '' }))
                    setTest(null)
                  }}
                  className={clsx(
                    'rounded-xl border p-3 text-left transition disabled:opacity-50',
                    form.providerId === p.id ? 'border-accent bg-accent-soft ring-2 ring-accent/20' : 'border-line hover:bg-elev-2',
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-medium text-fg">{p.name}</span>
                    {!p.hasApiKey && p.type !== 'OLLAMA' && <span className="chip !py-0.5 text-warn">no key</span>}
                  </div>
                  <div className="mt-0.5 truncate text-xs text-fg-3">
                    {TYPE_LABEL[p.type]}{p.baseUrl ? ` · ${p.baseUrl.replace(/^https?:\/\//, '')}` : ''}
                  </div>
                </button>
              ))}
            </div>
            {!saved.providerId && (
              <p className="mt-2 text-xs text-warn">
                Intelligence is still using its legacy settings ({saved.provider}). Select a provider above and save.
              </p>
            )}
          </fieldset>

          <div className="mt-6 grid gap-5 md:grid-cols-2">
            <div>
              <div className="flex items-center justify-between">
                <label className="label" htmlFor="model">Model</label>
                <button type="button" onClick={() => live.refetch()} className="mb-1.5 flex items-center gap-1 text-xs text-accent hover:underline">
                  <RefreshCw className={clsx('size-3', live.isFetching && 'animate-spin')} /> {models.length ? `${models.length} available` : 'Load models'}
                </button>
              </div>
              <input id="model" list="model-options" className="input font-mono" value={form.model} onChange={(e) => set('model', e.target.value)} placeholder="Pick or type a model id" required />
              <datalist id="model-options">{models.map((m) => <option key={m} value={m} />)}</datalist>
              <p className="mt-1.5 text-xs text-fg-3">Needs tool calling. Use “Test connection” to check.</p>
            </div>
            <div>
              <label className="label" htmlFor="fixModel">Model for writing fixes <span className="normal-case">(optional)</span></label>
              <input id="fixModel" list="model-options" className="input font-mono" value={form.fixModel} onChange={(e) => set('fixModel', e.target.value)} placeholder="Same as above" />
            </div>
          </div>

          <details className="group mt-5 rounded-xl border border-line">
            <summary className="flex cursor-pointer list-none items-center justify-between px-4 py-3 text-sm font-medium text-fg">
              Advanced <ChevronDown className="size-4 text-fg-3 transition group-open:rotate-180" />
            </summary>
            <div className="grid gap-4 border-t border-line p-4 sm:grid-cols-3">
              <div>
                <label className="label" htmlFor="maxTokens">Max output tokens</label>
                <input id="maxTokens" type="number" min={256} max={200000} className="input" value={form.maxTokens} onChange={(e) => set('maxTokens', Number(e.target.value))} />
              </div>
              <div>
                <label className="label" htmlFor="temperature">Temperature</label>
                <input id="temperature" type="number" step={0.05} min={0} max={2} className="input" value={form.temperature} onChange={(e) => set('temperature', Number(e.target.value))} />
              </div>
              <div>
                <label className="label" htmlFor="maxTokensParam">Token limit parameter</label>
                <select id="maxTokensParam" className="input" value={form.maxTokensParam} onChange={(e) => set('maxTokensParam', e.target.value)}>
                  <option value="max_tokens">max_tokens</option>
                  <option value="max_completion_tokens">max_completion_tokens (newer OpenAI)</option>
                </select>
              </div>
            </div>
          </details>

          {test && (
            <div
              role="status"
              className={clsx(
                'mt-5 flex items-start gap-3 rounded-xl px-4 py-3 text-sm',
                test.ok && test.toolCalling ? 'bg-ok/10 text-ok' : test.ok ? 'bg-warn/10 text-warn' : 'bg-err/10 text-err',
              )}
            >
              {test.ok && test.toolCalling ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : <XCircle className="mt-0.5 size-4 shrink-0" />}
              <div>
                <p className="font-medium">{test.ok ? (test.toolCalling ? 'Works' : 'Connected, but no tool call') : 'Connection failed'} <span className="font-normal opacity-75">· {test.model} · {(test.latencyMs / 1000).toFixed(1)} s</span></p>
                <p className="mt-0.5 break-words opacity-90">{test.detail}</p>
              </div>
            </div>
          )}

          <div className="mt-6 flex flex-wrap justify-end gap-2">
            {dirty && (
              <button type="button" className="btn-ghost" onClick={() => { setForm(fromSettings(saved)); setTest(null) }}>
                Discard changes
              </button>
            )}
            <button type="button" className="btn-secondary" disabled={!form.providerId || !form.model || runTest.isPending} onClick={() => runTest.mutate()}>
              {runTest.isPending ? <Loader2 className="size-4 animate-spin" /> : <Zap className="size-4" />} Test connection
            </button>
            <button type="button" className="btn-primary" disabled={!dirty || !form.providerId || !form.model || save.isPending} onClick={() => save.mutate()}>
              {save.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />} Save
            </button>
          </div>
        </>
      )}
    </section>
  )
}
