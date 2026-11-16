import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { BarChart3, Brain, Check, ChevronDown, CircleSlash, Compass, DatabaseZap, FileText, Network, X } from 'lucide-react'
import type { AgentRole, AgentRunPart, WorkflowPart } from '@/lib/types'
import { cn, formatDuration } from '@/lib/utils'
import { useServers } from '@/lib/queries'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { Spinner } from '@/components/ui/Spinner'
import { ToolCallCard } from './ToolCallCard'
import { Markdown } from './Markdown'

const ROLE: Record<AgentRole, { icon: React.ElementType; label: string }> = {
  planner: { icon: Compass, label: 'Planner' },
  retriever: { icon: DatabaseZap, label: 'Retriever' },
  analyst: { icon: Brain, label: 'Analyst' },
  visualizer: { icon: BarChart3, label: 'Visualizer' },
  assistant: { icon: Brain, label: 'Assistant' },
}

/** Ticks while something is running so elapsed timers stay live. */
function useNow(active: boolean) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!active) return
    const t = setInterval(() => setNow(Date.now()), 200)
    return () => clearInterval(t)
  }, [active])
  return now
}

export function WorkflowView({ workflow, live }: { workflow: WorkflowPart; live?: boolean }) {
  const [open, setOpen] = useState(!!live)
  const agents = workflow.agents
  const running = agents.filter((a) => a.status === 'running')
  const now = useNow(running.length > 0)
  const toolCount = agents.reduce((n, a) => n + a.tools.length, 0)
  const started = Math.min(...agents.map((a) => a.startedAt))
  const ended = Math.max(...agents.map((a) => (a.durationMs != null ? a.startedAt + a.durationMs : now)))
  const failed = agents.filter((a) => a.status === 'error').length

  const planner = agents.filter((a) => a.role === 'planner')
  const retrievers = agents.filter((a) => a.role === 'retriever')
  const synth = agents.filter((a) => a.role === 'analyst' || a.role === 'visualizer')

  const status = running.length
    ? running.some((a) => a.role === 'planner')
      ? 'Planning the approach…'
      : running.some((a) => a.role === 'retriever')
        ? `Retrieving from ${[...new Set(running.filter((a) => a.role === 'retriever').map((a) => a.server))].join(', ')}…`
        : running.some((a) => a.role === 'visualizer') && !running.some((a) => a.role === 'analyst')
          ? 'Drawing the chart…'
          : 'Analyzing results…'
    : `${agents.length} agent${agents.length === 1 ? '' : 's'} · ${toolCount} tool call${toolCount === 1 ? '' : 's'} · ${formatDuration(ended - started)}${failed ? ` · ${failed} failed` : ''}`

  return (
    <div className={cn('overflow-hidden rounded-2xl border bg-elev/70 backdrop-blur transition-colors', running.length ? 'border-accent/30' : 'border-line')}>
      <button onClick={() => setOpen((o) => !o)} className="flex w-full items-center gap-3 px-3.5 py-2.5 text-left transition hover:bg-elev-2/50">
        <span className={cn('relative flex size-8 shrink-0 items-center justify-center rounded-xl text-white', running.length ? 'bg-gradient-accent' : 'bg-fg/85')}>
          <Network className="size-4" />
          {running.length > 0 && <span className="absolute -inset-0.5 animate-ping rounded-xl bg-accent/30 [animation-duration:2s]" />}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-[13px] font-semibold">Agent workflow</span>
          <span className={cn('block truncate text-xs', running.length ? 'shimmer-text' : 'text-muted')}>{status}</span>
        </span>
        <span className="hidden items-center -space-x-1.5 sm:flex">
          {agents.map((a) => <AgentDot key={a.id} agent={a} />)}
        </span>
        <ChevronDown className={cn('size-4 shrink-0 text-muted transition-transform', open && 'rotate-180')} />
      </button>

      <AnimatePresence initial={false}>
        {open && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: 0.22 }}>
            <div className="border-t border-line px-3.5 pt-3 pb-1">
              {planner.length > 0 && (
                <Stage n={1} title="Plan" last={!retrievers.length && !synth.length}>
                  {planner.map((a) => <AgentCard key={a.id} agent={a} now={now} />)}
                  {workflow.plan && <PlanSummary plan={workflow.plan} />}
                </Stage>
              )}
              {retrievers.length > 0 && (
                <Stage n={2} title={`Retrieve${retrievers.length > 1 ? ` · ${retrievers.length} in parallel` : ''}`} last={!synth.length}>
                  <div className="grid gap-2 sm:grid-cols-2">{retrievers.map((a) => <AgentCard key={a.id} agent={a} now={now} />)}</div>
                </Stage>
              )}
              {synth.length > 0 && (
                <Stage n={retrievers.length ? 3 : 2} title="Synthesize" last>
                  <div className={cn('grid gap-2', synth.length > 1 && 'sm:grid-cols-2')}>{synth.map((a) => <AgentCard key={a.id} agent={a} now={now} />)}</div>
                </Stage>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function Stage({ n, title, last, children }: { n: number; title: string; last?: boolean; children: React.ReactNode }) {
  return (
    <div className="relative flex gap-3 pb-3">
      <div className="flex flex-col items-center">
        <span className="flex size-5 shrink-0 items-center justify-center rounded-full border border-line-strong bg-elev text-[10px] font-semibold text-muted">{n}</span>
        {!last && <span className="mt-1 w-px flex-1 bg-line-strong" />}
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        <div className="text-[11px] font-semibold tracking-wider text-muted uppercase">{title}</div>
        {children}
      </div>
    </div>
  )
}

function PlanSummary({ plan }: { plan: NonNullable<WorkflowPart['plan']> }) {
  return (
    <div className="rounded-xl border border-dashed border-line-strong px-3 py-2 text-xs text-fg-2">
      {plan.intent && <div className="mb-1"><span className="text-muted">Intent · </span>{plan.intent}</div>}
      <div className="flex flex-wrap gap-1.5">
        {plan.steps.length === 0 && <span className="text-muted">No data retrieval needed</span>}
        {plan.steps.map((s, i) => (
          <span key={i} className="rounded-md bg-elev-2 px-1.5 py-0.5"><span className="font-medium text-fg">{s.server}</span></span>
        ))}
        {plan.visualize && <span className="rounded-md bg-accent-soft px-1.5 py-0.5 text-accent">chart{plan.chartHint ? ` · ${plan.chartHint}` : ''}</span>}
        {plan.presentation && plan.presentation !== 'text' && <span className="rounded-md bg-elev-2 px-1.5 py-0.5">{plan.presentation}</span>}
        {plan.fallback && <span className="rounded-md bg-warn/10 px-1.5 py-0.5 text-warn">fallback plan</span>}
      </div>
    </div>
  )
}

function AgentDot({ agent }: { agent: AgentRunPart }) {
  const Icon = ROLE[agent.role]?.icon ?? Brain
  return (
    <span
      title={`${agent.name} · ${agent.status}`}
      className={cn(
        'flex size-6 items-center justify-center rounded-full border-2 border-elev',
        agent.status === 'running' ? 'bg-accent text-white' : agent.status === 'error' ? 'bg-err text-white' : agent.status === 'ok' ? 'bg-elev-2 text-fg-2' : 'bg-elev-2 text-muted',
      )}
    >
      <Icon className="size-3" />
    </span>
  )
}

function AgentCard({ agent, now }: { agent: AgentRunPart; now: number }) {
  const [showOutput, setShowOutput] = useState(false)
  const [openTool, setOpenTool] = useState<string | null>(null)
  const { data: servers } = useServers()
  const server = agent.serverId ? servers?.find((s) => s.id === agent.serverId) : undefined
  const Icon = ROLE[agent.role]?.icon ?? Brain
  const elapsed = agent.durationMs ?? now - agent.startedAt
  const running = agent.status === 'running'

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      className={cn(
        'rounded-xl border bg-elev p-2.5 transition-colors',
        running ? 'border-accent/40 shadow-[0_0_0_3px_var(--accent-soft)]' : agent.status === 'error' ? 'border-err/30' : 'border-line',
      )}
    >
      <div className="flex items-center gap-2">
        {server || agent.server ? (
          <ServerIcon name={agent.server ?? ''} icon={server?.icon} color={server?.color} size="sm" />
        ) : (
          <span className={cn('flex size-7 items-center justify-center rounded-lg', running ? 'bg-accent-soft text-accent' : 'bg-elev-2 text-fg-2')}>
            <Icon className="size-3.5" />
          </span>
        )}
        <div className="min-w-0 flex-1">
          <div className="truncate text-[13px] font-medium">{agent.name}</div>
          <div className="text-[11px] text-muted tabular-nums">{formatDuration(Math.max(0, elapsed))}{agent.tools.length ? ` · ${agent.tools.length} tool${agent.tools.length > 1 ? 's' : ''}` : ''}</div>
        </div>
        <StatusPill status={agent.status} />
      </div>
      {agent.goal && <p className="mt-1.5 line-clamp-2 text-xs text-fg-2">{agent.goal}</p>}

      {agent.tools.length > 0 && (
        <div className="mt-2 space-y-1">
          {agent.tools.map((t) =>
            openTool === t.id ? (
              <div key={t.id} onDoubleClick={() => setOpenTool(null)}><ToolCallCard part={t} /></div>
            ) : (
              <button key={t.id} onClick={() => setOpenTool(t.id)} className="flex w-full items-center gap-2 rounded-lg bg-elev-2/70 px-2 py-1 text-left text-[11px] transition hover:bg-elev-2">
                {t.status === 'running' ? <Spinner className="size-3 text-accent" /> : t.status === 'ok' ? <Check className="size-3 text-ok" strokeWidth={3} /> : <X className="size-3 text-err" strokeWidth={3} />}
                <span className="truncate font-mono text-fg">{t.tool}</span>
                <span className="ml-auto shrink-0 text-muted">{formatDuration(t.durationMs)}</span>
              </button>
            ),
          )}
        </div>
      )}

      {agent.summary && !running && (
        <p className={cn('mt-2 line-clamp-2 text-xs', agent.status === 'error' ? 'text-err' : 'text-muted')}>{agent.summary}</p>
      )}
      {agent.output && !running && (
        <>
          <button onClick={() => setShowOutput((s) => !s)} className="mt-1.5 inline-flex items-center gap-1 text-[11px] font-medium text-accent hover:underline">
            <FileText className="size-3" /> {showOutput ? 'Hide report' : 'View report'}
          </button>
          {showOutput && (
            <div className="mt-2 max-h-72 overflow-auto rounded-lg border border-line bg-code p-3 text-[13px] [&_.md]:text-[13px]">
              <Markdown text={agent.output} />
            </div>
          )}
        </>
      )}
    </motion.div>
  )
}

function StatusPill({ status }: { status: AgentRunPart['status'] }) {
  if (status === 'running') return <Spinner className="size-4 text-accent" />
  if (status === 'ok') return <span className="flex size-5 items-center justify-center rounded-full bg-ok/15"><Check className="size-3 text-ok" strokeWidth={3} /></span>
  if (status === 'error') return <span className="flex size-5 items-center justify-center rounded-full bg-err/15"><X className="size-3 text-err" strokeWidth={3} /></span>
  return <CircleSlash className="size-4 text-muted" />
}
