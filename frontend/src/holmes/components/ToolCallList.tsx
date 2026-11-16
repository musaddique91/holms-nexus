import clsx from 'clsx'
import { Check, CheckCircle2, ChevronRight, Copy, Terminal, XCircle } from 'lucide-react'
import { useState } from 'react'
import type { ToolCall } from '../api/types'

function prettyArgs(raw: string): string {
  try {
    const parsed = JSON.parse(raw)
    const entries = Object.entries(parsed)
    if (entries.length === 0) return ''
    return entries
      .map(([k, v]) => `${k}=${typeof v === 'string' ? v : JSON.stringify(v)}`)
      .join('  ')
  } catch {
    return raw
  }
}

function Row({ call }: { call: ToolCall }) {
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const copyOutput = (e: React.MouseEvent) => {
    e.stopPropagation()
    navigator.clipboard.writeText(call.output)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <li className="border-b border-line/60 last:border-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left transition-colors hover:bg-subtle/70"
        aria-expanded={open}
      >
        <ChevronRight
          className={clsx('size-3.5 shrink-0 text-fg-3 transition-transform duration-150', open && 'rotate-90')}
        />
        {call.success ? (
          <CheckCircle2 className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
        ) : (
          <XCircle className="size-4 shrink-0 text-red-600 dark:text-red-400" />
        )}
        <span className="font-mono text-xs font-semibold text-fg">{call.toolName}</span>
        <span className="min-w-0 flex-1 truncate font-mono text-[11px] text-fg-3">{prettyArgs(call.arguments)}</span>
        <span className="shrink-0 font-mono text-[11px] text-fg-3 tabular-nums">{call.durationMs} ms</span>
      </button>

      {open && (
        <div className="relative mx-4 mb-3 overflow-hidden rounded-xl border border-slate-800 bg-[#090d16] p-4 text-xs font-mono shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2 text-[11px] text-slate-400">
            <span className="flex items-center gap-1.5">
              <Terminal className="size-3.5" />
              <span>Tool Output</span>
            </span>
            <button
              type="button"
              onClick={copyOutput}
              className="flex items-center gap-1 rounded bg-slate-800/80 px-2 py-0.5 text-[10px] text-slate-300 hover:bg-slate-700"
            >
              {copied ? <Check className="size-3 text-emerald-400" /> : <Copy className="size-3" />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>
          <pre className="max-h-96 overflow-auto whitespace-pre-wrap text-slate-200 leading-relaxed">
            {call.output || '(No output returned)'}
          </pre>
        </div>
      )}
    </li>
  )
}

export function ToolCallList({ calls, live }: { calls: ToolCall[]; live?: boolean }) {
  if (calls.length === 0) {
    return (
      <p className="px-4 py-6 text-center text-xs text-fg-3">
        {live ? 'Waiting for the first tool invocation…' : 'No tools were executed during this investigation.'}
      </p>
    )
  }
  return (
    <ol className="divide-y divide-line/60">
      {calls.map((c) => (
        <Row key={c.sequence} call={c} />
      ))}
      {live && (
        <li className="flex items-center gap-2 px-4 py-3 text-xs text-violet-700 dark:text-violet-300 bg-violet-500/5">
          <span className="size-2 rounded-full bg-current animate-ping" />
          <span>Investigating next step…</span>
        </li>
      )}
    </ol>
  )
}
