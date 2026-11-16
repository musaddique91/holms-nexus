import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  ChevronRight,
  GitPullRequest,
  Layers,
  Radar,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import NumberFlow from '@number-flow/react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { SEVERITY_BAR, StatusBadge, statusLabel } from '../components/Badges'
import { ErrorNote } from '../components/Page'
import { ListSkeleton, Skeleton } from '../components/Skeleton'
import { compact, timeAgo } from '../lib/format'

const OPEN = ['NEW', 'INVESTIGATING', 'INVESTIGATED', 'FIXING', 'PR_OPENED', 'FAILED']
const STATUS_COLOR: Record<string, string> = {
  NEW: 'bg-blue-500',
  INVESTIGATING: 'bg-violet-500',
  INVESTIGATED: 'bg-amber-500',
  FIXING: 'bg-indigo-400',
  PR_OPENED: 'bg-emerald-500',
  FAILED: 'bg-rose-500',
  RESOLVED: 'bg-slate-400/40',
  IGNORED: 'bg-slate-400/20',
}

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

function Stat({
  label,
  value,
  hint,
  icon: Icon,
  tone,
  to,
}: {
  label: string
  value: number | undefined
  hint?: string
  icon: typeof Activity
  tone: { bg: string; text: string; ring: string }
  to?: string
}) {
  const body = (
    <div className="card-interactive group relative flex h-full flex-col justify-between overflow-hidden p-5">
      <div className="flex items-start justify-between">
        <div className={clsx('grid size-11 place-items-center rounded-xl shadow-2xs ring-1', tone.bg, tone.text, tone.ring)}>
          <Icon className="size-5" strokeWidth={2.2} />
        </div>
        {to && (
          <div className="rounded-lg p-1 text-fg-3 opacity-0 transition-opacity duration-150 group-hover:opacity-100">
            <ArrowUpRight className="size-4" />
          </div>
        )}
      </div>
      <div className="mt-5">
        <div className="text-3xl font-bold tracking-tight tabular-nums text-fg">
          {value === undefined ? <Skeleton className="h-9 w-16" /> : <NumberFlow value={value} />}
        </div>
        <div className="mt-1 text-xs font-semibold text-fg-2">{label}</div>
        {hint && <div className="mt-0.5 text-[11px] text-fg-3">{hint}</div>}
      </div>
    </div>
  )
  return to ? (
    <Link to={to} className="block focus:outline-none">
      {body}
    </Link>
  ) : (
    body
  )
}

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard, refetchInterval: 15_000 })
  const recent = useQuery({ queryKey: ['incidents', 'recent'], queryFn: () => api.incidents({}), refetchInterval: 15_000 })
  const d = dashboard.data
  const open = d ? OPEN.reduce((sum, s) => sum + (d.byStatus[s] ?? 0), 0) : undefined
  const total = d ? Object.values(d.byStatus).reduce((a, b) => a + b, 0) : 0
  const maxTop = Math.max(1, ...(d?.topServices7d.map((s) => s.incidents) ?? [1]))

  return (
    <div className="space-y-7">
      {/* Hero / Greeting Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-line/60 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-fg md:text-3xl">{greeting()}</h1>
            {open !== undefined && (
              <span
                className={clsx(
                  'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium',
                  open === 0
                    ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                    : 'border-amber-500/25 bg-amber-500/10 text-amber-700 dark:text-amber-300',
                )}
              >
                <span
                  className={clsx('size-1.5 rounded-full', open === 0 ? 'bg-emerald-500' : 'bg-amber-500 animate-pulse')}
                />
                {open === 0 ? 'All Systems Healthy' : `${open} Active Issue${open === 1 ? '' : 's'}`}
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-fg-3">
            {d === undefined
              ? 'Connecting to telemetry pipeline…'
              : open === 0
                ? 'All quiet across monitored microservices and infrastructure.'
                : `${open} open incident${open === 1 ? '' : 's'} · ${d.byStatus.INVESTIGATING ?? 0} active AI investigation${d.byStatus.INVESTIGATING === 1 ? '' : 's'} in progress`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link to="/holmes/ask" className="btn-secondary text-xs">
            <Sparkles className="size-3.5 text-accent" />
            <span>Ask Intelligence</span>
          </Link>
          <Link to="/holmes/incidents" className="btn-primary text-xs">
            <span>View All Incidents</span>
            <ChevronRight className="size-3.5" />
          </Link>
        </div>
      </div>

      <ErrorNote error={dashboard.error} />

      {/* KPI Cards */}
      <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <Stat
          label="Open incidents"
          value={open}
          hint="requiring attention"
          icon={open && open > 0 ? ShieldAlert : Activity}
          tone={{
            bg: 'bg-red-500/10 dark:bg-red-500/15',
            text: 'text-red-600 dark:text-red-400',
            ring: 'ring-red-500/20',
          }}
          to="/holmes/incidents"
        />
        <Stat
          label="New in 24 hours"
          value={d?.newIncidents24h}
          hint="distinct error clusters"
          icon={Radar}
          tone={{
            bg: 'bg-blue-500/10 dark:bg-blue-500/15',
            text: 'text-blue-600 dark:text-blue-400',
            ring: 'ring-blue-500/20',
          }}
        />
        <Stat
          label="AI investigations (24h)"
          value={d?.investigations24h}
          hint="automated root-cause analyses"
          icon={Sparkles}
          tone={{
            bg: 'bg-violet-500/10 dark:bg-violet-500/15',
            text: 'text-violet-600 dark:text-violet-400',
            ring: 'ring-violet-500/20',
          }}
        />
        <Stat
          label="Fix PRs prepared"
          value={d?.pullRequestsOpened}
          hint="autonomous pull requests"
          icon={GitPullRequest}
          tone={{
            bg: 'bg-emerald-500/10 dark:bg-emerald-500/15',
            text: 'text-emerald-600 dark:text-emerald-400',
            ring: 'ring-emerald-500/20',
          }}
        />
      </div>

      {/* Incident Status Breakdown Bar */}
      {d && total > 0 && (
        <section className="card p-5" aria-label="Incident status breakdown">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Layers className="size-4 text-fg-3" />
              <h2 className="text-sm font-semibold tracking-tight text-fg">Status Distribution</h2>
            </div>
            <span className="font-mono text-xs text-fg-3">{total} total incidents</span>
          </div>

          <div className="flex h-2.5 overflow-hidden rounded-full bg-subtle p-0.5 ring-1 ring-line/80">
            {Object.entries(d.byStatus).map(([s, n]) => {
              const width = (n / total) * 100
              if (width === 0) return null
              return (
                <div
                  key={s}
                  className={clsx('h-full first:rounded-l-full last:rounded-r-full transition-all duration-300', STATUS_COLOR[s] ?? 'bg-fg-3')}
                  style={{ width: `${width}%` }}
                  title={`${statusLabel(s)}: ${n} (${Math.round(width)}%)`}
                />
              )
            })}
          </div>

          <ul className="mt-3.5 flex flex-wrap gap-x-5 gap-y-2">
            {Object.entries(d.byStatus).map(([s, n]) => (
              <li key={s}>
                <Link
                  to={`/holmes/incidents?status=${s}`}
                  className="group flex items-center gap-1.5 text-xs text-fg-2 transition-colors hover:text-fg"
                >
                  <span className={clsx('size-2 rounded-full', STATUS_COLOR[s] ?? 'bg-fg-3')} />
                  <span className="font-medium group-hover:underline">{statusLabel(s)}</span>
                  <span className="font-mono text-[11px] text-fg-3 tabular-nums">({n})</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Main Content Split: Latest Incidents + Sidebar */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Latest Incidents List */}
        <section className="card overflow-hidden lg:col-span-2" aria-label="Latest incidents">
          <div className="flex items-center justify-between border-b border-line/60 px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold tracking-tight text-fg">Latest Incidents</h2>
              <p className="text-xs text-fg-3">Live error events ingested across all services</p>
            </div>
            <Link to="/holmes/incidents" className="text-xs font-medium text-accent hover:underline">
              View all
            </Link>
          </div>

          {!recent.data && (
            <div className="p-4">
              <ListSkeleton rows={5} />
            </div>
          )}

          <div className="divide-y divide-line/60">
            {recent.data?.items.slice(0, 7).map((i) => (
              <Link
                key={i.id}
                to={`/holmes/incidents/${i.id}`}
                className="group flex items-center gap-3.5 px-5 py-3.5 transition-colors duration-150 hover:bg-subtle/70"
              >
                <span className={clsx('h-9 w-1 shrink-0 rounded-full', SEVERITY_BAR[i.severity ?? ''] ?? 'bg-line')} aria-hidden />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate text-sm font-semibold tracking-tight text-fg group-hover:text-accent transition-colors">
                      {i.title}
                    </span>
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-fg-3">
                    <span className="font-medium text-fg-2">{i.serviceName}</span>
                    {i.environment && (
                      <span className="rounded bg-subtle px-1.5 py-0.2 font-mono text-[10px] text-fg-3 border border-line/60">
                        {i.environment}
                      </span>
                    )}
                    <span>·</span>
                    <span className="font-mono tabular-nums">{compact(i.occurrenceCount)}× events</span>
                    <span>·</span>
                    <span>{timeAgo(i.lastSeenAt)}</span>
                  </div>
                </div>
                <div className="hidden sm:block shrink-0">
                  <StatusBadge status={i.status} />
                </div>
                <ChevronRight className="size-4 shrink-0 text-fg-3 transition-transform duration-150 group-hover:translate-x-0.5 group-hover:text-fg" />
              </Link>
            ))}

            {recent.data && recent.data.items.length === 0 && (
              <div className="flex flex-col items-center px-4 py-16 text-center">
                <div className="grid size-12 place-items-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 ring-1 ring-emerald-500/20">
                  <ShieldCheck className="size-6" />
                </div>
                <p className="mt-3 text-sm font-semibold text-fg">No incidents detected</p>
                <p className="mt-1 max-w-sm text-xs text-fg-3">
                  Telemetry pipelines are connected. Newly captured error traces will appear here automatically.
                </p>
              </div>
            )}
          </div>
        </section>

        {/* Sidebar: Noisiest Services & Integrations */}
        <div className="space-y-6">
          {/* Top Services */}
          <section className="card p-5" aria-label="Noisiest services">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-semibold tracking-tight text-fg">Impacted Services (7d)</h2>
              <span className="text-[11px] font-medium text-fg-3 uppercase tracking-wider">Incidents</span>
            </div>

            {!d && <ListSkeleton rows={3} />}
            {d?.topServices7d.length === 0 && <p className="text-sm text-fg-3">No service errors recorded this week.</p>}

            <ul className="space-y-3.5">
              {d?.topServices7d.map((s, idx) => (
                <li key={s.service} className="space-y-1.5">
                  <div className="flex items-baseline justify-between gap-2 text-xs">
                    <div className="flex items-center gap-1.5 min-w-0">
                      <span className="font-mono text-[10px] text-fg-3">{idx + 1}.</span>
                      <Link
                        className="truncate font-medium text-fg hover:text-accent hover:underline"
                        to={`/holmes/incidents?service=${encodeURIComponent(s.service)}`}
                      >
                        {s.service}
                      </Link>
                    </div>
                    <span className="shrink-0 font-mono text-[11px] text-fg-3 tabular-nums">
                      <strong className="font-semibold text-fg">{s.incidents}</strong> · {compact(s.occurrences)} evt
                    </span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-subtle ring-1 ring-line/60">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 transition-all duration-300"
                      style={{ width: `${(s.incidents / maxTop) * 100}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          </section>

          {/* Connected Toolsets & Notification Channels */}
          <section className="card p-5 space-y-4" aria-label="Connections">
            <div className="flex items-center justify-between border-b border-line/60 pb-3">
              <h2 className="text-sm font-semibold tracking-tight text-fg">Active Integrations</h2>
              <Link to="/holmes/integrations" className="text-xs text-accent hover:underline">
                Manage
              </Link>
            </div>

            <div>
              <p className="label">Investigation Toolsets</p>
              <div className="flex flex-wrap gap-1.5">
                {d?.enabledToolsets.map((t) => (
                  <span key={t} className="chip text-[11px]">
                    <span className="size-1.5 rounded-full bg-emerald-500 shadow-[0_0_6px_rgba(16,185,129,0.5)]" />
                    {t}
                  </span>
                ))}
                {d?.enabledToolsets.length === 0 && (
                  <span className="text-xs text-amber-700 dark:text-amber-300">None enabled</span>
                )}
              </div>
            </div>

            <div>
              <p className="label">Dispatch Channels</p>
              <div className="flex flex-wrap gap-1.5">
                {d?.notificationChannels.map((t) => (
                  <span key={t} className="chip text-[11px]">
                    <CheckCircle2 className="size-3 text-emerald-500" />
                    {t}
                  </span>
                ))}
                {d?.notificationChannels.length === 0 && (
                  <Link to="/holmes/integrations" className="text-xs text-amber-700 hover:underline dark:text-amber-300">
                    Configure Slack, Email or Telegram
                  </Link>
                )}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
