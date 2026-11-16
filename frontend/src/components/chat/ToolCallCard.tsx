import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Check, ChevronRight, CircleAlert, Wrench } from 'lucide-react'
import type { ToolPart } from '@/lib/types'
import { cn, formatDuration, prettyJson } from '@/lib/utils'
import { Spinner } from '@/components/ui/Spinner'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { CopyButton } from './CodeBlock'
import { useServers } from '@/lib/queries'

const MAX_PREVIEW = 4000

export function ToolCallCard({ part }: { part: ToolPart }) {
  const [open, setOpen] = useState(false)
  const [full, setFull] = useState(false)
  const { data: servers } = useServers()
  const server = servers?.find((s) => s.id === part.serverId)
  const args = JSON.stringify(part.args ?? {}, null, 2)
  const { pretty } = prettyJson(part.result)
  const shown = full ? pretty : pretty.slice(0, MAX_PREVIEW)
  const argSummary = Object.entries(part.args ?? {})
    .map(([k, v]) => `${k}: ${typeof v === 'string' ? v : JSON.stringify(v)}`)
    .join(', ')

  return (
    <div
      className={cn(
        'overflow-hidden rounded-xl border bg-elev/70 transition-colors',
        part.status === 'error' ? 'border-err/25' : 'border-line',
        open && 'border-line-strong',
      )}
    >
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full cursor-pointer items-center gap-2.5 px-3 py-2 text-left transition hover:bg-elev-2/60"
      >
        <ServerIcon icon={server?.icon} color={server?.color} name={part.server} size="xs" />
        <div className="flex min-w-0 flex-1 items-baseline gap-2">
          <span className="hidden shrink-0 text-[13px] text-muted sm:inline">{part.server}</span>
          <span className="hidden text-muted/50 sm:inline">›</span>
          <span className="min-w-0 truncate font-mono text-[13px] font-medium text-fg sm:shrink-0">{part.tool}</span>
          {argSummary && <span className="hidden min-w-0 truncate font-mono text-xs text-muted/80 sm:inline">{argSummary}</span>}
        </div>
        <span className="flex shrink-0 items-center gap-2 text-xs text-muted">
          {part.status === 'running' ? (
            <span className="flex items-center gap-1.5 text-accent">
              <Spinner className="size-3.5" /> <span className="shimmer-text">Running</span>
            </span>
          ) : part.status === 'ok' ? (
            <>
              {formatDuration(part.durationMs)}
              <span className="flex size-4 items-center justify-center rounded-full bg-ok/15"><Check className="size-3 text-ok" strokeWidth={3} /></span>
            </>
          ) : (
            <>
              {formatDuration(part.durationMs)}
              <CircleAlert className="size-4 text-err" />
            </>
          )}
          <ChevronRight className={cn('size-4 transition-transform', open && 'rotate-90')} />
        </span>
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            <div className="space-y-3 border-t border-line px-3 py-3">
              <Section title="Arguments" copy={args}>
                <pre className="max-h-60 overflow-auto font-mono text-xs leading-relaxed text-fg-2">{args}</pre>
              </Section>
              {part.status !== 'running' && (
                <Section title={part.status === 'error' ? 'Error' : 'Result'} copy={pretty}>
                  <pre className={cn('max-h-96 overflow-auto font-mono text-xs leading-relaxed whitespace-pre-wrap break-words', part.status === 'error' ? 'text-err' : 'text-fg-2')}>
                    {shown || <span className="text-muted italic">(empty)</span>}
                  </pre>
                  {pretty.length > MAX_PREVIEW && (
                    <button className="mt-2 text-xs font-medium text-accent hover:underline" onClick={() => setFull((f) => !f)}>
                      {full ? 'Show less' : `Show all (${(pretty.length / 1000).toFixed(1)}k chars)`}
                    </button>
                  )}
                </Section>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function Section({ title, copy, children }: { title: string; copy: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-[11px] font-semibold tracking-wider text-muted uppercase">
          <Wrench className="size-3" /> {title}
        </span>
        <CopyButton text={copy} />
      </div>
      <div className="rounded-lg border border-line bg-code p-3">{children}</div>
    </div>
  )
}
