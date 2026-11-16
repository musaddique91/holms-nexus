import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  ArrowLeft,
  CheckCheck,
  Clock,
  Cpu,
  ExternalLink,
  EyeOff,
  FileCode,
  GitPullRequest,
  RefreshCw,
  Server,
  ShieldAlert,
  Sparkles,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Investigation, Remediation } from '../api/types'
import { ConfidenceBadge, SeverityBadge, StatusBadge } from '../components/Badges'
import { ErrorNote } from '../components/Page'
import { Markdown } from '../components/Markdown'
import { ListSkeleton } from '../components/Skeleton'
import { toast } from 'sonner'
import { ToolCallList } from '../components/ToolCallList'
import { compact, dateTime, duration, timeAgo } from '../lib/format'

type Tab = 'analysis' | 'steps' | 'fix' | 'error'

export function IncidentDetailPage() {
  const id = Number(useParams().id)
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('analysis')

  const detail = useQuery({
    queryKey: ['incident', id],
    queryFn: () => api.incident(id),
    enabled: Number.isFinite(id),
    refetchInterval: (q) => {
      const s = q.state.data?.incident.status
      return s === 'NEW' || s === 'INVESTIGATING' || s === 'FIXING' ? 3_000 : 30_000
    },
  })

  const action = useMutation({
    mutationFn: (a: 'reinvestigate' | 'fix' | 'resolve' | 'ignore') => api.incidentAction(id, a),
    onSuccess: (_res, a) => {
      queryClient.invalidateQueries({ queryKey: ['incident', id] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      const messages = {
        reinvestigate: ['Queued for fresh investigation', 'Intelligence will start within a few seconds.'],
        fix: ['Intelligence is preparing a code fix', 'A draft pull request will appear under “Fix / PR”.'],
        resolve: ['Incident resolved', 'It will automatically re-open if the error reoccurs.'],
        ignore: ['Incident ignored', 'Further occurrences will be silently tallied.'],
      } as const
      toast.success(messages[a][0], { description: messages[a][1] })
    },
    onError: (e) => toast.error('Action failed', { description: e instanceof Error ? e.message : undefined }),
  })

  const data = detail.data
  if (!Number.isFinite(id)) {
    return <ErrorNote error="Incident not found" />
  }
  if (!data) {
    return (
      <>
        {detail.error ? (
          <ErrorNote error={detail.error} />
        ) : (
          <div className="card p-6">
            <ListSkeleton rows={5} />
          </div>
        )}
      </>
    )
  }

  const { incident, investigations, remediations } = data
  const latest: Investigation | undefined = investigations[0]
  const busy = incident.status === 'INVESTIGATING' || incident.status === 'FIXING'

  const tabs: { id: Tab; label: string; count?: number }[] = [
    { id: 'analysis', label: 'Root Cause' },
    { id: 'steps', label: 'Investigation Steps', count: latest?.toolCalls.length },
    { id: 'fix', label: 'Fix / PR', count: remediations.length },
    { id: 'error', label: 'Raw Error' },
  ]

  return (
    <div className="space-y-6">
      {/* Navigation Breadcrumb */}
      <div>
        <Link
          to="/holmes/incidents"
          className="group inline-flex items-center gap-1.5 text-xs font-medium text-fg-3 transition-colors hover:text-fg"
        >
          <ArrowLeft className="size-3.5 transition-transform duration-150 group-hover:-translate-x-0.5" />
          <span>Back to Incidents</span>
        </Link>
      </div>

      {/* Main Incident Hero Card */}
      <div className="card p-6 md:p-7 space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          {/* basis: below ~26rem of room the actions wrap under the title instead of squeezing it */}
          <div className="min-w-0 flex-1 basis-[26rem] space-y-2.5">
            <div className="flex flex-wrap items-center gap-2">
              <SeverityBadge severity={incident.severity} />
              <StatusBadge status={incident.status} />
              <span className="font-mono text-xs font-semibold text-fg-3">#{incident.id}</span>
              {incident.environment && (
                <span className="rounded-md border border-line bg-subtle px-2 py-0.5 font-mono text-[11px] font-medium text-fg-2">
                  {incident.environment}
                </span>
              )}
            </div>

            <h1 className="text-xl font-bold tracking-tight text-fg md:text-2xl break-words">
              {incident.title}
            </h1>

            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs text-fg-3">
              <span className="flex items-center gap-1 font-medium text-fg-2">
                <Server className="size-3.5 text-fg-3" />
                {incident.serviceName}
              </span>
              {incident.host && <span>· host: <code className="font-mono">{incident.host}</code></span>}
              <span>·</span>
              <span className="font-mono tabular-nums">{compact(incident.occurrenceCount)} occurrences</span>
              <span>·</span>
              <span className="flex items-center gap-1">
                <Clock className="size-3 text-fg-3" />
                first seen {timeAgo(incident.firstSeenAt)} · last seen {timeAgo(incident.lastSeenAt)}
              </span>
            </div>
          </div>

          {/* Action Toolbar */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              className="btn-secondary text-xs h-9"
              disabled={busy || action.isPending}
              onClick={() => action.mutate('reinvestigate')}
            >
              <RefreshCw className={clsx('size-3.5', (busy || action.isPending) && 'animate-spin')} />
              <span>Re-investigate</span>
            </button>

            <button
              className="btn-primary text-xs h-9"
              disabled={busy || action.isPending || !latest || latest.status !== 'COMPLETED'}
              onClick={() => action.mutate('fix')}
              title="Autonomous fix generation"
            >
              <GitPullRequest className="size-3.5" />
              <span>Create Fix PR</span>
            </button>

            <button
              className="btn-secondary text-xs h-9"
              disabled={busy || action.isPending}
              onClick={() => action.mutate('resolve')}
            >
              <CheckCheck className="size-3.5 text-emerald-600" />
              <span>Resolve</span>
            </button>

            <button
              className="btn-secondary text-xs h-9"
              disabled={busy || action.isPending}
              onClick={() => action.mutate('ignore')}
            >
              <EyeOff className="size-3.5" />
              <span>Ignore</span>
            </button>
          </div>
        </div>
      </div>

      {/* Segmented Control Tabs (HIG / Material 3) */}
      <div className="border-b border-line/60">
        <div className="flex gap-1 overflow-x-auto pb-px" role="tablist" aria-label="Incident views">
          {tabs.map((t) => (
            <button
              key={t.id}
              role="tab"
              type="button"
              aria-selected={tab === t.id}
              onClick={() => setTab(t.id)}
              className={clsx(
                'relative flex items-center gap-2 px-4 py-2.5 text-xs font-semibold whitespace-nowrap transition-colors duration-150',
                tab === t.id
                  ? 'text-accent border-b-2 border-accent'
                  : 'text-fg-3 hover:text-fg',
              )}
            >
              <span>{t.label}</span>
              {t.count !== undefined && t.count > 0 && (
                <span
                  className={clsx(
                    'rounded-full px-1.5 py-0.2 font-mono text-[10px] font-bold',
                    tab === t.id ? 'bg-accent/15 text-accent' : 'bg-subtle text-fg-3',
                  )}
                >
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Tab Panels */}
      {tab === 'analysis' && <AnalysisTab investigation={latest} status={incident.status} />}
      {tab === 'steps' && (
        <div className="card overflow-hidden">
          {latest ? (
            <>
              <div className="flex flex-wrap items-center justify-between border-b border-line bg-subtle/50 px-5 py-3 text-xs text-fg-3">
                <div className="flex items-center gap-2">
                  <Cpu className="size-4 text-accent" />
                  <span className="font-semibold text-fg">{latest.model}</span>
                  <span>·</span>
                  <span>{latest.toolCalls.length} tool executions</span>
                </div>
                <div className="font-mono tabular-nums">
                  {duration(latest.startedAt, latest.finishedAt)} · {compact(latest.inputTokens)} in / {compact(latest.outputTokens)} out tokens
                </div>
              </div>
              <ToolCallList calls={latest.toolCalls} live={latest.status === 'RUNNING'} />
            </>
          ) : (
            <p className="p-6 text-sm text-fg-3">No investigation runs recorded yet.</p>
          )}
        </div>
      )}
      {tab === 'fix' && <FixTab remediations={remediations} />}
      {tab === 'error' && (
        <div className="card space-y-6 p-6">
          <div>
            <div className="label">Sample Error Message</div>
            <pre className="overflow-x-auto rounded-xl border border-line bg-subtle p-4 font-mono text-xs whitespace-pre-wrap text-fg">
              {incident.sampleMessage}
            </pre>
          </div>

          {incident.sampleStackTrace && (
            <div>
              <div className="label">Stack Trace</div>
              <pre className="max-h-[32rem] overflow-auto rounded-xl border border-slate-800 bg-[#090d16] p-4 font-mono text-xs text-slate-100">
                {incident.sampleStackTrace}
              </pre>
            </div>
          )}

          <div className="rounded-xl border border-line bg-subtle/40 p-4">
            <dl className="grid grid-cols-2 gap-4 text-xs sm:grid-cols-4">
              <div>
                <dt className="label">Exception Type</dt>
                <dd className="font-mono font-medium text-fg break-all">{incident.errorType ?? '—'}</dd>
              </div>
              <div>
                <dt className="label">Logger</dt>
                <dd className="font-mono font-medium text-fg break-all">{incident.logger ?? '—'}</dd>
              </div>
              <div>
                <dt className="label">Telemetry Source</dt>
                <dd className="font-medium text-fg">{incident.source ?? '—'}</dd>
              </div>
              <div>
                <dt className="label">First Logged</dt>
                <dd className="font-mono text-fg-2">{dateTime(incident.firstSeenAt)}</dd>
              </div>
            </dl>
          </div>
        </div>
      )}
    </div>
  )
}

function AnalysisTab({ investigation: inv, status }: { investigation?: Investigation; status: string }) {
  if (!inv) {
    return (
      <div className="card p-10 text-center">
        <div className="mx-auto grid size-12 place-items-center rounded-2xl bg-elev-2 text-fg-3 mb-3">
          <Sparkles className="size-6" />
        </div>
        <p className="text-sm font-semibold text-fg">
          {status === 'NEW' ? 'Queued for Autonomous Investigation' : 'No investigation available yet.'}
        </p>
        <p className="mt-1 text-xs text-fg-3">
          Intelligence starts root-cause analysis automatically when new error alerts arrive.
        </p>
      </div>
    )
  }

  if (inv.status === 'RUNNING') {
    return (
      <div className="card p-8 text-center space-y-3">
        <div className="mx-auto size-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />
        <h3 className="text-base font-semibold text-fg">Intelligence is investigating…</h3>
        <p className="text-xs text-fg-3 max-w-md mx-auto">
          Inspecting container logs, database queries and git history. {inv.toolCalls.length} tool calls executed so far.
        </p>
      </div>
    )
  }

  if (inv.status === 'FAILED') {
    return (
      <div className="card p-6 border-red-500/20 bg-red-500/5 text-sm text-red-700 dark:text-red-300">
        <h3 className="font-semibold mb-1">Investigation failed</h3>
        <p className="text-xs">{inv.error}</p>
      </div>
    )
  }

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      {/* Left 2 Cols: Summary, Root Cause & Suggested Fix */}
      <div className="space-y-6 lg:col-span-2">
        <section className="card p-6 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line/60 pb-3">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold tracking-tight text-fg">AI Analysis Summary</h2>
              <ConfidenceBadge confidence={inv.confidence} />
            </div>
            {inv.category && (
              <span className="rounded-md border border-line bg-subtle px-2 py-0.5 font-mono text-[10px] font-semibold uppercase text-fg-2">
                {inv.category.replace('_', ' ')}
              </span>
            )}
          </div>

          <p className="text-sm leading-relaxed text-fg">{inv.summary}</p>

          <div className="border-t border-line/60 pt-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-fg-3 mb-2 flex items-center gap-1.5">
              <ShieldAlert className="size-3.5 text-amber-500" />
              Root Cause
            </h3>
            <div className="prose-holmes">
              <Markdown>{inv.rootCause}</Markdown>
            </div>
          </div>

          <div className="border-t border-line/60 pt-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-fg-3 mb-2 flex items-center gap-1.5">
              <Sparkles className="size-3.5 text-accent" />
              Suggested Fix
            </h3>
            <div className="prose-holmes">
              <Markdown>{inv.suggestedFix}</Markdown>
            </div>
          </div>
        </section>

        {inv.report && (
          <section className="card p-6">
            <h2 className="text-sm font-bold tracking-tight text-fg mb-3">Comprehensive Investigation Report</h2>
            <div className="prose-holmes">
              <Markdown>{inv.report}</Markdown>
            </div>
          </section>
        )}
      </div>

      {/* Right Column: Evidence & Metadata */}
      <div className="space-y-6">
        <section className="card p-5 space-y-3">
          <h2 className="text-xs font-bold uppercase tracking-wider text-fg-3">Key Evidence</h2>
          <ul className="space-y-2 text-xs text-fg-2">
            {inv.evidence.map((e, i) => (
              <li key={i} className="flex items-start gap-2 rounded-lg bg-subtle/50 p-2.5 border border-line/50">
                <span className="size-1.5 rounded-full bg-accent mt-1.5 shrink-0" />
                <span className="leading-relaxed">{e}</span>
              </li>
            ))}
          </ul>
        </section>

        {inv.suspectedFiles.length > 0 && (
          <section className="card p-5 space-y-3">
            <h2 className="text-xs font-bold uppercase tracking-wider text-fg-3">Suspected Files</h2>
            <ul className="space-y-1.5 font-mono text-xs text-fg-2">
              {inv.suspectedFiles.map((f) => (
                <li key={f} className="flex items-center gap-2 rounded-lg bg-subtle px-2.5 py-1.5 border border-line/50 break-all">
                  <FileCode className="size-3.5 text-accent shrink-0" />
                  <span>{f}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Model telemetry pill */}
        <section className="card p-5 text-xs text-fg-3 space-y-2 bg-subtle/30">
          <div className="flex items-center gap-1.5 text-fg font-semibold">
            <Cpu className="size-3.5 text-accent" />
            <span>Agent Telemetry</span>
          </div>
          <div className="font-mono text-[11px] leading-relaxed space-y-1">
            <div>Provider: {inv.provider} · {inv.model}</div>
            <div>Workflow: {inv.steps} steps · {inv.toolCallCount} tools · {duration(inv.startedAt, inv.finishedAt)}</div>
            <div>Tokens: {compact(inv.inputTokens)} in · {compact(inv.outputTokens)} out</div>
          </div>
        </section>
      </div>
    </div>
  )
}

function FixTab({ remediations }: { remediations: Remediation[] }) {
  if (remediations.length === 0) {
    return (
      <div className="card p-12 text-center max-w-lg mx-auto">
        <div className="mx-auto grid size-12 place-items-center rounded-2xl bg-accent-soft text-accent mb-3">
          <GitPullRequest className="size-6" />
        </div>
        <h3 className="text-sm font-semibold text-fg">No pull request generated yet</h3>
        <p className="mt-1 text-xs text-fg-3 leading-relaxed">
          Intelligence creates a branch and draft PR automatically when auto-fix is configured for the service. You can also trigger it manually using the "Create Fix PR" button above.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {remediations.map((r) => (
        <section key={r.id} className="card overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-subtle/50 px-5 py-3.5">
            <div className="flex items-center gap-2.5">
              <StatusBadge status={r.status} />
              <span className="text-sm font-semibold text-fg">{r.title ?? 'Remediation Proposal'}</span>
            </div>
            {r.prUrl?.startsWith('https://') && (
              <a
                href={r.prUrl}
                target="_blank"
                rel="noreferrer noopener"
                className="btn-primary text-xs h-8 px-3"
              >
                <GitPullRequest className="size-3.5" />
                <span>View PR {r.prNumber ? `#${r.prNumber}` : ''}</span>
                <ExternalLink className="size-3" />
              </a>
            )}
          </div>

          <div className="space-y-4 p-5 md:p-6">
            {r.reason && (
              <div className="rounded-xl border border-line bg-subtle/60 p-3.5 text-xs text-fg-2">
                <strong className="font-semibold text-fg">Outcome:</strong> {r.reason}
              </div>
            )}
            {r.error && <p className="text-sm text-red-700 dark:text-red-300">{r.error}</p>}
            {r.description && (
              <div className="prose-holmes">
                <Markdown>{r.description}</Markdown>
              </div>
            )}

            {r.filesChanged.length > 0 && (
              <div>
                <div className="label">Changed Files</div>
                <ul className="space-y-1 font-mono text-xs text-fg-2">
                  {r.filesChanged.map((f) => (
                    <li key={f} className="flex items-center gap-2 rounded-lg bg-subtle px-2.5 py-1 border border-line/60">
                      <FileCode className="size-3 text-accent" />
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {r.diff && (
              <div>
                <div className="label">Unified Code Diff</div>
                <pre className="max-h-[32rem] overflow-auto rounded-xl border border-slate-800 bg-[#090d16] p-4 font-mono text-xs leading-5">
                  {r.diff.split('\n').map((line, i) => (
                    <div
                      key={i}
                      className={clsx(
                        line.startsWith('+') && !line.startsWith('+++')
                          ? 'bg-emerald-950/40 text-emerald-300'
                          : line.startsWith('-') && !line.startsWith('---')
                            ? 'bg-red-950/40 text-rose-300'
                            : 'text-slate-300',
                      )}
                    >
                      {line || ' '}
                    </div>
                  ))}
                </pre>
              </div>
            )}
          </div>

          {r.toolCalls.length > 0 && (
            <details className="border-t border-line/60">
              <summary className="cursor-pointer px-5 py-2.5 text-xs font-medium text-fg-3 hover:text-fg">
                {r.toolCalls.length} tool executions during remediation
              </summary>
              <ToolCallList calls={r.toolCalls} />
            </details>
          )}
        </section>
      ))}
    </div>
  )
}
