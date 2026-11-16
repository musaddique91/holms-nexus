import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  History,
  MessageSquareText,
  Send,
  Sparkles,
} from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { StatusBadge } from '../components/Badges'
import { ErrorNote, PageHeader } from '../components/Page'
import { Markdown } from '../components/Markdown'
import { ToolCallList } from '../components/ToolCallList'
import { duration, timeAgo } from '../lib/format'

const EXAMPLES = [
  'Which containers restarted in the last hour and why?',
  'Is orders-api healthy right now? Check logs, container state and database.',
  'Show the top errors per service in OpenObserve over the last 24 hours.',
  'Is there consumer lag on the holms.errors Kafka topic?',
]

export function AskPage() {
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  const [question, setQuestion] = useState(() => params.get('q') ?? '')
  const [selected, setSelected] = useState<number | null>(null)

  const history = useQuery({ queryKey: ['chat'], queryFn: api.chatHistory, refetchInterval: 10_000 })
  const current = useQuery({
    queryKey: ['investigation', selected],
    queryFn: () => api.investigation(selected!),
    enabled: selected !== null,
    refetchInterval: (q) => (q.state.data?.status === 'RUNNING' ? 2_000 : false),
  })

  const ask = useMutation({
    mutationFn: (q: string) => api.ask(q),
    onSuccess: ({ id }) => {
      setSelected(id)
      setQuestion('')
      queryClient.invalidateQueries({ queryKey: ['chat'] })
    },
  })

  const inv = current.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Ask Intelligence"
        subtitle="Autonomous interactive investigations with live read-only evidence from your services and telemetry"
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        {/* Main Content Area: Prompt Box & Result */}
        <div className="min-w-0 space-y-6">
          {/* Prompt Composer Card */}
          <form
            className="card relative overflow-hidden p-4 shadow-sm focus-within:border-accent/50 focus-within:shadow-glow/20 transition-all duration-200"
            onSubmit={(e) => {
              e.preventDefault()
              if (question.trim() && !ask.isPending) ask.mutate(question.trim())
            }}
          >
            <div className="flex items-start gap-3">
              <div className="grid size-8 shrink-0 place-items-center rounded-xl bg-gradient-to-tr from-accent to-violet-500 text-white shadow-2xs">
                <Sparkles className="size-4" />
              </div>
              <textarea
                className="w-full min-h-24 resize-y bg-transparent text-sm text-fg placeholder:text-fg-3/70 outline-none leading-relaxed"
                placeholder="Ask about system status, logs, Kafka consumer lag, container crashes, or database health…"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                    e.preventDefault()
                    if (question.trim() && !ask.isPending) ask.mutate(question.trim())
                  }
                }}
                aria-label="Question"
              />
            </div>

            {/* Quick Example Prompt Pills */}
            <div className="mt-3 flex flex-wrap gap-1.5 border-t border-line/60 pt-3">
              <span className="text-[11px] font-semibold text-fg-3 py-1">Try:</span>
              {EXAMPLES.map((ex) => (
                <button
                  key={ex}
                  type="button"
                  className="rounded-full border border-line bg-subtle/80 px-2.5 py-1 text-xs text-fg-2 transition hover:bg-subtle hover:text-fg hover:border-line/90 active:scale-[0.98]"
                  onClick={() => setQuestion(ex)}
                >
                  {ex}
                </button>
              ))}
            </div>

            {/* Action Bar */}
            <div className="mt-3 flex items-center justify-between border-t border-line/60 pt-3">
              <div className="hidden sm:flex items-center gap-1.5 font-mono text-[11px] text-fg-3">
                <kbd className="rounded border border-line bg-subtle px-1.5 py-0.5 font-sans">⌘</kbd>
                <span>+</span>
                <kbd className="rounded border border-line bg-subtle px-1.5 py-0.5 font-sans">Enter</kbd>
                <span>to send</span>
              </div>
              <button
                type="submit"
                className="btn-primary text-xs h-9 px-4 ml-auto"
                disabled={ask.isPending || !question.trim()}
              >
                {ask.isPending ? (
                  <>
                    <span className="size-3.5 rounded-full border-2 border-white border-t-transparent animate-spin" />
                    <span>Investigating…</span>
                  </>
                ) : (
                  <>
                    <Send className="size-3.5" />
                    <span>Run Query</span>
                  </>
                )}
              </button>
            </div>
          </form>

          <ErrorNote error={ask.error ?? current.error} />

          {/* Investigation Result Card */}
          {inv && (
            <section className="card overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-subtle/50 px-5 py-3.5">
                <div className="flex items-center gap-2 min-w-0 flex-1">
                  <MessageSquareText className="size-4 text-accent shrink-0" />
                  <p className="truncate text-sm font-semibold text-fg">{inv.question}</p>
                </div>
                <StatusBadge status={inv.status} />
              </div>

              <div className="p-6">
                {inv.status === 'RUNNING' && (
                  <div className="flex items-center gap-3 text-sm text-violet-700 dark:text-violet-300">
                    <span className="size-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
                    <span>Gathering evidence from integrations… ({inv.toolCalls.length} tool calls completed so far)</span>
                  </div>
                )}
                {inv.status === 'FAILED' && (
                  <div className="rounded-xl border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-700 dark:text-red-300">
                    <strong className="font-semibold">Query failed:</strong> {inv.error}
                  </div>
                )}
                {inv.status === 'COMPLETED' && (
                  <div className="prose-holmes">
                    <Markdown>{inv.report}</Markdown>
                  </div>
                )}
              </div>

              <details className="border-t border-line/60 bg-subtle/30" open={inv.status === 'RUNNING'}>
                <summary className="cursor-pointer px-5 py-2.5 text-xs font-medium text-fg-3 hover:text-fg flex items-center justify-between">
                  <span>{inv.toolCalls.length} tool calls executed · {duration(inv.startedAt, inv.finishedAt)}</span>
                  <span className="text-[11px] text-accent">Toggle execution trace</span>
                </summary>
                <div className="border-t border-line/60 bg-surface">
                  <ToolCallList calls={inv.toolCalls} live={inv.status === 'RUNNING'} />
                </div>
              </details>
            </section>
          )}
        </div>

        {/* History Sidebar */}
        <aside className="card h-fit overflow-hidden">
          <div className="flex items-center gap-2 border-b border-line bg-subtle/50 px-4 py-3">
            <History className="size-4 text-fg-3" />
            <h2 className="text-xs font-bold uppercase tracking-wider text-fg-3">Query History</h2>
          </div>

          <ul className="max-h-[36rem] divide-y divide-line/60 overflow-y-auto">
            {history.data?.map((h) => (
              <li key={h.id}>
                <button
                  type="button"
                  onClick={() => {
                    ask.reset()
                    setSelected(h.id)
                  }}
                  aria-current={selected === h.id}
                  className={clsx(
                    'w-full p-3.5 text-left transition-colors duration-150',
                    selected === h.id
                      ? 'bg-accent/10 border-l-2 border-accent'
                      : 'hover:bg-subtle/70',
                  )}
                >
                  <div className="line-clamp-2 text-xs font-medium text-fg">{h.question}</div>
                  <div className="mt-1 flex items-center justify-between text-[11px] text-fg-3">
                    <span>{timeAgo(h.startedAt)}</span>
                    <span className="font-mono text-[10px] uppercase font-semibold">{h.status.toLowerCase()}</span>
                  </div>
                </button>
              </li>
            ))}
            {history.data?.length === 0 && (
              <li className="px-4 py-8 text-center text-xs text-fg-3">
                No past questions yet. Ask something above!
              </li>
            )}
          </ul>
        </aside>
      </div>
    </div>
  )
}
