import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  Plug,
  RefreshCw,
  Wrench,
} from 'lucide-react'
import { useState } from 'react'
import { api } from '../api/client'
import { ErrorNote, PageHeader } from '../components/Page'
import { ListSkeleton } from '../components/Skeleton'

export function IntegrationsPage() {
  const [probe, setProbe] = useState(true)
  const toolsets = useQuery({ queryKey: ['toolsets', probe], queryFn: () => api.toolsets(probe) })

  return (
    <>
      <PageHeader
        title="Integrations & Toolsets"
        subtitle="Telemetry, infrastructure, and investigation tools available to Intelligence. Enabled via environment variables and holmes.yml."
        actions={
          <button
            className="btn-secondary text-xs h-9 px-3.5"
            onClick={() => {
              setProbe(true)
              toolsets.refetch()
            }}
            disabled={toolsets.isFetching}
          >
            <RefreshCw className={clsx('size-3.5', toolsets.isFetching && 'animate-spin')} />
            <span>Health Check</span>
          </button>
        }
      />

      <ErrorNote error={toolsets.error} />

      {toolsets.isLoading && !toolsets.data && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <div className="card p-5"><ListSkeleton rows={4} /></div>
          <div className="card p-5"><ListSkeleton rows={4} /></div>
          <div className="card p-5"><ListSkeleton rows={4} /></div>
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {toolsets.data?.map((t) => (
          <section
            key={t.name}
            className={clsx(
              'card p-5 transition-all duration-200 flex flex-col justify-between space-y-4',
              !t.enabled && 'opacity-65 border-dashed',
            )}
          >
            <div>
              <div className="flex items-center justify-between gap-2 border-b border-line/60 pb-3">
                <div className="flex items-center gap-2">
                  <div className="grid size-7 place-items-center rounded-lg bg-subtle border border-line text-fg-2">
                    <Plug className="size-3.5" />
                  </div>
                  <h2 className="font-mono text-xs font-bold text-fg">{t.name}</h2>
                </div>

                {!t.enabled ? (
                  <span className="rounded-full border border-line bg-subtle px-2 py-0.5 text-[11px] font-medium text-fg-3">
                    Disabled
                  </span>
                ) : t.healthy === null ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                    <span className="size-1.5 rounded-full bg-blue-500" />
                    Enabled
                  </span>
                ) : t.healthy ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
                    <span className="size-1.5 rounded-full bg-emerald-500 shadow-[0_0_6px_rgba(16,185,129,0.5)]" />
                    Healthy
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-red-500/20 bg-red-500/10 px-2.5 py-0.5 text-[11px] font-medium text-red-700 dark:text-red-300">
                    <span className="size-1.5 rounded-full bg-red-500 animate-pulse" />
                    Unreachable
                  </span>
                )}
              </div>

              <p className="mt-3 text-xs leading-relaxed text-fg-2">{t.description}</p>

              {t.message && (
                <div
                  className={clsx(
                    'mt-3 rounded-lg border p-2.5 text-xs font-mono break-all',
                    t.healthy
                      ? 'border-line bg-subtle/40 text-fg-3'
                      : 'border-red-500/20 bg-red-500/5 text-red-700 dark:text-red-300',
                  )}
                >
                  {t.message}
                </div>
              )}
            </div>

            <details className="border-t border-line/60 pt-3 group">
              <summary className="cursor-pointer text-xs font-medium text-fg-3 hover:text-fg flex items-center justify-between">
                <span className="flex items-center gap-1.5">
                  <Wrench className="size-3" />
                  <span>{t.tools.length} Available Tools</span>
                </span>
                <span className="text-[10px] text-accent font-semibold group-open:hidden">View</span>
              </summary>
              <ul className="mt-2.5 flex flex-wrap gap-1.5">
                {t.tools.map((tool) => (
                  <li
                    key={tool}
                    className="rounded-md border border-line bg-subtle px-2 py-0.5 font-mono text-[11px] text-fg-2"
                  >
                    {tool}
                  </li>
                ))}
              </ul>
            </details>
          </section>
        ))}
      </div>
    </>
  )
}
