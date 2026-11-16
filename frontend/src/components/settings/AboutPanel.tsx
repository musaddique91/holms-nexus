import { BarChart3, Database, KeyRound, Plug, Wrench } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { Kbd } from '@/components/ui/Badge'
import { modKey } from '@/lib/utils'
import { PanelHeader } from './SettingsDialog'

const STEPS = [
  { icon: Plug, title: 'Connect MCP servers', body: 'Local commands (npx, uvx, docker) or remote HTTP endpoints. Each server exposes tools, resources and prompts.' },
  { icon: KeyRound, title: 'Pick a model', body: 'Anthropic, any OpenAI-compatible API, or a local Ollama — switch per chat from the composer.' },
  { icon: Wrench, title: 'Ask in plain language', body: 'The model decides which tools to call. You see every call, its arguments and result, live.' },
  { icon: BarChart3, title: 'Understand the data', body: 'Answers come back as markdown tables and interactive charts you can discuss further.' },
]

const SHORTCUTS: [string[], string][] = [
  [[modKey, 'K'], 'Command palette'],
  [[modKey, '⇧', 'O'], 'New chat'],
  [[modKey, 'B'], 'Toggle sidebar'],
  [[modKey, ','], 'Settings'],
  [['⇧', '↵'], 'New line in composer'],
]

export function AboutPanel() {
  return (
    <div>
      <PanelHeader title="About MCP" description="A workspace for talking to your data through the Model Context Protocol." />
      <div className="max-w-3xl space-y-8 p-6">
        <div className="flex items-center gap-4 rounded-2xl border border-line bg-gradient-to-br from-accent-soft to-transparent p-5">
          <Logo size={48} />
          <div>
            <div className="text-lg font-semibold">MCP <span className="text-sm font-normal text-muted">v0.1.0</span></div>
            <div className="text-sm text-muted">React · Spring Boot 4 · Spring AI 2 · MCP Java SDK · PostgreSQL · Redis</div>
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          {STEPS.map((s, i) => (
            <div key={s.title} className="rounded-2xl border border-line p-4">
              <div className="mb-2 flex items-center gap-2">
                <span className="flex size-7 items-center justify-center rounded-lg bg-accent-soft text-accent"><s.icon className="size-4" /></span>
                <span className="text-xs text-muted">Step {i + 1}</span>
              </div>
              <div className="font-semibold">{s.title}</div>
              <p className="mt-1 text-sm text-muted">{s.body}</p>
            </div>
          ))}
        </div>
        <section>
          <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold"><Database className="size-4" /> Keyboard shortcuts</h3>
          <div className="divide-y divide-line rounded-2xl border border-line">
            {SHORTCUTS.map(([keys, label]) => (
              <div key={label} className="flex items-center justify-between px-4 py-2.5 text-sm">
                <span className="text-fg-2">{label}</span>
                <span className="flex gap-1">{keys.map((k) => <Kbd key={k}>{k}</Kbd>)}</span>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
