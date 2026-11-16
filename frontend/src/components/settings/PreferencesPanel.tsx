import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { RotateCcw } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { useProviders, useSettings } from '@/lib/queries'
import type { AppSettings } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { Switch } from '@/components/ui/Switch'
import { Field, Input, Textarea } from '@/components/ui/Input'
import { PanelHeader } from './SettingsDialog'

export function PreferencesPanel() {
  const { data: settings } = useSettings()
  const { data: providers = [] } = useProviders()
  const qc = useQueryClient()
  const [s, setS] = useState<AppSettings | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => { if (settings) setS(settings) }, [settings])

  const save = async () => {
    if (!s) return
    setBusy(true)
    try {
      const saved = await api.settings.update(s)
      qc.setQueryData(['settings'], saved)
      toast.success('Preferences saved')
    } catch (e) {
      toast.error('Could not save', { description: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }

  const provider = providers.find((p) => p.id === s?.defaultProviderId)
  const dirty = JSON.stringify(s) !== JSON.stringify(settings)

  return (
    <div>
      <PanelHeader
        title="Preferences"
        description="Appearance and how the assistant behaves."
        actions={<Button variant="primary" size="sm" onClick={save} loading={busy} disabled={!dirty}>Save changes</Button>}
      />
      <div className="max-w-3xl space-y-8 p-6">

        {s && (
          <>
            <section className="space-y-4">
              <h3 className="text-sm font-semibold">Default model for new chats</h3>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Provider">
                  <select
                    value={s.defaultProviderId ?? ''}
                    onChange={(e) => setS({ ...s, defaultProviderId: e.target.value || null, defaultModel: null })}
                    className="h-9 w-full rounded-xl border border-line bg-elev px-3 text-sm outline-none focus:border-accent/60"
                  >
                    <option value="">Automatic (first ready provider)</option>
                    {providers.map((p) => <option key={p.id} value={p.id} disabled={!p.ready}>{p.name}{p.ready ? '' : ' (needs key)'}</option>)}
                  </select>
                </Field>
                <Field label="Model">
                  <select
                    value={s.defaultModel ?? ''}
                    disabled={!provider}
                    onChange={(e) => setS({ ...s, defaultModel: e.target.value || null })}
                    className="h-9 w-full rounded-xl border border-line bg-elev px-3 font-mono text-sm outline-none focus:border-accent/60 disabled:opacity-50"
                  >
                    <option value="">{provider ? `Provider default (${provider.defaultModel})` : '—'}</option>
                    {provider?.models.map((m) => <option key={m} value={m}>{m}</option>)}
                  </select>
                </Field>
              </div>
            </section>

            <section className="space-y-3">
              <h3 className="text-sm font-semibold">Agents</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                {([
                  ['multi', 'Multi-agent workflow', 'Planner → parallel retrievers → analyst + visualizer. Best answers, live workflow view.'],
                  ['single', 'Single agent (fast)', 'One agent with every tool. Fewer model calls — good for small/local models.'],
                ] as const).map(([id, title, body]) => (
                  <button key={id} onClick={() => setS({ ...s, agentMode: id })}
                    className={cn('rounded-xl border p-3 text-left transition', s.agentMode === id ? 'border-accent/60 bg-accent-soft ring-4 ring-accent/10' : 'border-line hover:border-line-strong')}>
                    <div className="text-sm font-medium">{title}</div>
                    <div className="mt-0.5 text-xs text-muted">{body}</div>
                  </button>
                ))}
              </div>
              {s.agentMode === 'multi' && (
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="flex items-center justify-between gap-3 rounded-xl border border-line px-3 py-2.5">
                    <div>
                      <div className="text-sm font-medium">Visualizer agent</div>
                      <div className="text-xs text-muted">Draw a Highcharts chart when data suits one</div>
                    </div>
                    <Switch checked={s.visualizer} onChange={(v) => setS({ ...s, visualizer: v })} />
                  </div>
                  <Field label={<span className="flex w-full justify-between">Max parallel retrievers <span className="font-mono text-muted">{s.maxParallelAgents}</span></span>}>
                    <input type="range" min={1} max={8} step={1} value={s.maxParallelAgents} onChange={(e) => setS({ ...s, maxParallelAgents: Number(e.target.value) })} className="w-full accent-[var(--accent)]" />
                  </Field>
                </div>
              )}
            </section>

            <section className="space-y-4">
              <h3 className="text-sm font-semibold">Generation</h3>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label={<span className="flex w-full justify-between">Temperature <span className="font-mono text-muted">{s.temperature.toFixed(1)}</span></span>} hint="Lower is more precise, higher is more creative.">
                  <input type="range" min={0} max={1.5} step={0.1} value={s.temperature} onChange={(e) => setS({ ...s, temperature: Number(e.target.value) })} className="w-full accent-[var(--accent)]" />
                </Field>
                <Field label="Max output tokens">
                  <Input type="number" min={256} max={64000} step={256} value={s.maxTokens} onChange={(e) => setS({ ...s, maxTokens: Number(e.target.value) })} />
                </Field>
              </div>
            </section>

            <section className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold">System prompt</h3>
                <Button size="sm" variant="ghost" onClick={async () => setS({ ...s, systemPrompt: (await api.settings.defaultPrompt()).systemPrompt })}>
                  <RotateCcw className="size-3.5" /> Reset to default
                </Button>
              </div>
              <Textarea className="min-h-[240px] font-mono text-[13px]" value={s.systemPrompt} onChange={(e) => setS({ ...s, systemPrompt: e.target.value })} />
              <p className="text-xs text-muted">Chart (Highcharts) and table formatting guides are added to the agents automatically.</p>
            </section>
          </>
        )}
      </div>
    </div>
  )
}
