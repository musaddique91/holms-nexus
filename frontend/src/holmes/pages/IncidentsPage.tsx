import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Filter, Search, X } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { IncidentStatus } from '../api/types'
import { SeverityBadge, StatusBadge } from '../components/Badges'
import { ErrorNote, PageHeader } from '../components/Page'
import { ListSkeleton } from '../components/Skeleton'
import { compact, timeAgo } from '../lib/format'

const STATUSES: IncidentStatus[] = ['NEW', 'INVESTIGATING', 'INVESTIGATED', 'FIXING', 'PR_OPENED', 'FAILED', 'RESOLVED', 'IGNORED']

export function IncidentsPage() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as IncidentStatus | ''
  const service = params.get('service') ?? ''
  const q = params.get('q') ?? ''
  const rawPage = Number(params.get('page') ?? 0)
  const page = Number.isFinite(rawPage) && rawPage > 0 ? Math.floor(rawPage) : 0

  const services = useQuery({ queryKey: ['services'], queryFn: api.services })
  const incidents = useQuery({
    queryKey: ['incidents', status, service, q, page],
    queryFn: () => api.incidents({ status, service, q, page }),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  })

  function update(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next)
  }

  function clearFilters() {
    setParams(new URLSearchParams())
  }

  const data = incidents.data
  const hasFilters = Boolean(status || service || q)

  return (
    <>
      <PageHeader
        title="Incidents"
        subtitle="Grouped occurrences of distinct application errors with autonomous root-cause analysis"
      />

      {/* Filter Bar */}
      <div className="mb-4 flex flex-col gap-2.5 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-fg-3" />
          <input
            key={q}
            className="input pl-9 pr-8"
            placeholder="Search error title, stacktrace or exception… (Press Enter)"
            defaultValue={q}
            onKeyDown={(e) => e.key === 'Enter' && update('q', (e.target as HTMLInputElement).value)}
            onBlur={(e) => e.target.value !== q && update('q', e.target.value)}
            aria-label="Search"
          />
          {q && (
            <button
              type="button"
              onClick={() => update('q', '')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-fg-3 hover:text-fg"
              aria-label="Clear search"
            >
              <X className="size-4" />
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            className="input sm:w-44 text-xs font-medium"
            value={status}
            onChange={(e) => update('status', e.target.value)}
            aria-label="Status filter"
          >
            <option value="">All Statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.replace('_', ' ').toLowerCase()}
              </option>
            ))}
          </select>

          <select
            className="input sm:w-44 text-xs font-medium"
            value={service}
            onChange={(e) => update('service', e.target.value)}
            aria-label="Service filter"
          >
            <option value="">All Services</option>
            {services.data?.map((s) => (
              <option key={s.id} value={s.name}>
                {s.displayName ?? s.name}
              </option>
            ))}
          </select>

          {hasFilters && (
            <button
              type="button"
              onClick={clearFilters}
              className="btn-ghost text-xs text-accent hover:text-accent-hover h-9 px-2.5"
            >
              Clear filters
            </button>
          )}
        </div>
      </div>

      <ErrorNote error={incidents.error} />

      {/* Incident Data Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line bg-subtle/70 text-[11px] font-semibold uppercase tracking-wider text-fg-3">
              <tr>
                <th className="px-5 py-3">Incident / Error</th>
                <th className="px-5 py-3">Service</th>
                <th className="px-5 py-3 text-right">Events</th>
                <th className="px-5 py-3">Last Seen</th>
                <th className="px-5 py-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/60">
              {incidents.isLoading && !data && (
                <tr>
                  <td colSpan={5} className="p-6">
                    <ListSkeleton rows={6} />
                  </td>
                </tr>
              )}

              {data?.items.map((i) => (
                <tr
                  key={i.id}
                  className="group transition-colors duration-150 hover:bg-subtle/60"
                >
                  <td className="max-w-md px-5 py-3.5">
                    <Link to={`/holmes/incidents/${i.id}`} className="flex items-center gap-2.5">
                      <SeverityBadge severity={i.severity} />
                      <span className="truncate font-medium text-fg group-hover:text-accent transition-colors">
                        {i.title}
                      </span>
                    </Link>
                  </td>
                  <td className="px-5 py-3.5 whitespace-nowrap text-xs text-fg-2">
                    <span className="font-medium text-fg">{i.serviceName}</span>
                    {i.environment && (
                      <span className="ml-1.5 rounded bg-subtle px-1.5 py-0.5 font-mono text-[10px] text-fg-3 border border-line/60">
                        {i.environment}
                      </span>
                    )}
                  </td>
                  <td className="px-5 py-3.5 text-right font-mono text-xs tabular-nums text-fg-2">
                    {compact(i.occurrenceCount)}
                  </td>
                  <td className="px-5 py-3.5 whitespace-nowrap text-xs text-fg-3">
                    {timeAgo(i.lastSeenAt)}
                  </td>
                  <td className="px-5 py-3.5">
                    <StatusBadge status={i.status} />
                  </td>
                </tr>
              ))}

              {data && data.items.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-5 py-16 text-center">
                    <div className="mx-auto grid size-12 place-items-center rounded-2xl bg-elev-2 text-fg-3 mb-3">
                      <Filter className="size-5" />
                    </div>
                    <p className="text-sm font-semibold text-fg">No incidents matched your criteria</p>
                    <p className="mt-1 text-xs text-fg-3">
                      Try adjusting or clearing your search keywords or status filters.
                    </p>
                    {hasFilters && (
                      <button
                        type="button"
                        onClick={clearFilters}
                        className="btn-secondary mt-4 text-xs"
                      >
                        Reset filters
                      </button>
                    )}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pagination Controls */}
      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between gap-4 text-xs">
          <span className="text-fg-3">
            Showing page <strong className="font-semibold text-fg">{data.page + 1}</strong> of{' '}
            <strong className="font-semibold text-fg">{data.totalPages}</strong> ({data.total} total)
          </span>
          <div className="flex items-center gap-2">
            <button
              className="btn-secondary text-xs h-8 px-2.5"
              disabled={page === 0}
              onClick={() => update('page', String(page - 1))}
            >
              <ChevronLeft className="size-3.5" />
              <span>Previous</span>
            </button>
            <button
              className="btn-secondary text-xs h-8 px-2.5"
              disabled={page + 1 >= data.totalPages}
              onClick={() => update('page', String(page + 1))}
            >
              <span>Next</span>
              <ChevronRight className="size-3.5" />
            </button>
          </div>
        </div>
      )}
    </>
  )
}
