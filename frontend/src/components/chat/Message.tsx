import { memo } from 'react'
import { motion } from 'motion/react'
import { AlertTriangle, RotateCcw } from 'lucide-react'
import type { ChatMessage, Part } from '@/lib/types'
import { cn, formatDuration } from '@/lib/utils'
import { Markdown } from './Markdown'
import { ToolCallCard } from './ToolCallCard'
import { WorkflowView } from './WorkflowView'
import { HighchartsBlock } from '@/components/data/HighchartsBlock'
import type { HOptions } from '@/lib/highcharts'
import { CopyButton } from './CodeBlock'
import { Logo } from '@/components/layout/Logo'

export const UserMessage = memo(function UserMessage({ content, fresh = false }: { content: string; fresh?: boolean }) {
  return (
    <motion.div initial={fresh ? { opacity: 0, y: 8 } : false} animate={{ opacity: 1, y: 0 }} className="group flex justify-end">
      <div className="flex max-w-[85%] flex-col items-end gap-1">
        <div className="rounded-2xl rounded-br-md border border-line bg-elev-2 px-4 py-2.5 text-[15px] leading-relaxed whitespace-pre-wrap break-words text-fg shadow-soft">
          {content}
        </div>
        <div className="opacity-0 transition group-hover:opacity-100">
          <CopyButton text={content} />
        </div>
      </div>
    </motion.div>
  )
})

function Parts({ parts, streaming }: { parts: Part[]; streaming: boolean }) {
  return (
    <div className="space-y-3">
      {parts.map((p, i) =>
        p.type === 'workflow' ? (
          <WorkflowView key="workflow" workflow={p} live={streaming} />
        ) : p.type === 'tool' ? (
          <ToolCallCard key={p.id} part={p} />
        ) : p.type === 'chart' ? (
          <HighchartsBlock key={p.id} options={p.options as HOptions} />
        ) : (
          <div key={i} className={cn(streaming && i === parts.length - 1 && 'caret-host')}>
            <Markdown text={p.text} streaming={streaming && i === parts.length - 1} />
          </div>
        ),
      )}
    </div>
  )
}

export function AssistantShell({ children, footer, fresh = false }: { children: React.ReactNode; footer?: React.ReactNode; fresh?: boolean }) {
  return (
    <motion.div initial={fresh ? { opacity: 0, y: 8 } : false} animate={{ opacity: 1, y: 0 }} className="group flex gap-3.5">
      <div className="mt-0.5 shrink-0">
        <Logo size={28} />
      </div>
      <div className="min-w-0 flex-1 space-y-2">
        {children}
        {footer}
      </div>
    </motion.div>
  )
}

export const AssistantMessage = memo(function AssistantMessage({ message, isLast, onRegenerate, busy }: {
  message: ChatMessage
  isLast: boolean
  onRegenerate?: () => void
  busy?: boolean
}) {
  const parts: Part[] = message.parts?.length ? message.parts : [{ type: 'text', text: message.content }]
  const textOnly = parts.filter((p) => p.type === 'text').map((p) => (p as { text: string }).text).join('\n\n')
  const hasText = parts.some((p) => p.type === 'text' && p.text.trim())
  return (
    <AssistantShell
      footer={
        <div className={cn('flex items-center gap-1 text-xs text-muted transition', isLast ? 'opacity-100' : 'opacity-0 group-hover:opacity-100')}>
          <CopyButton text={textOnly || message.content} />
          {isLast && onRegenerate && (
            <button
              onClick={onRegenerate}
              disabled={busy}
              className="inline-flex items-center gap-1.5 rounded-md px-1.5 py-1 transition hover:bg-elev-2 hover:text-fg disabled:opacity-40"
              aria-label="Regenerate"
            >
              <RotateCcw className="size-3.5" />
            </button>
          )}
          <span className="ml-1.5 truncate">
            {message.model}
            {message.durationMs ? ` · ${formatDuration(message.durationMs)}` : ''}
          </span>
        </div>
      }
    >
      {message.error && !hasText && !parts.some((p) => p.type === 'workflow') ? (
        <ErrorNote text={message.content} />
      ) : (
        <>
          <Parts parts={parts.filter((p) => !(p.type === 'text' && message.error && p.text === message.content))} streaming={false} />
          {message.error && <ErrorNote text={message.content.split('⚠️ ').pop() || message.content} />}
        </>
      )}
    </AssistantShell>
  )
})

export function ErrorNote({ text }: { text: string }) {
  return (
    <div className="flex items-start gap-2.5 rounded-xl border border-err/20 bg-err/5 px-3.5 py-3 text-sm text-err">
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <span className="break-words">{text}</span>
    </div>
  )
}

export function StreamingMessage({ parts, status, error }: { parts: Part[]; status: string; error?: string }) {
  const running = status === 'streaming'
  const lastTool = [...parts].reverse().find((p) => p.type === 'tool')
  const hasWorkflow = parts.some((p) => p.type === 'workflow')
  const waiting = running && !hasWorkflow && (parts.length === 0 || parts[parts.length - 1].type === 'tool')
  return (
    <AssistantShell fresh>
      {parts.length > 0 && <Parts parts={parts} streaming={running} />}
      {waiting && (
        <div className="flex items-center gap-2 py-1 text-sm">
          <span className="flex gap-1">
            {[0, 1, 2].map((i) => (
              <motion.span
                key={i}
                className="size-1.5 rounded-full bg-accent"
                animate={{ opacity: [0.25, 1, 0.25], y: [0, -2, 0] }}
                transition={{ duration: 1.1, repeat: Infinity, delay: i * 0.15 }}
              />
            ))}
          </span>
          <span className="shimmer-text">
            {lastTool && lastTool.type === 'tool' && lastTool.status === 'running'
              ? `Querying ${lastTool.server}…`
              : parts.length
                ? 'Analyzing results…'
                : 'Thinking…'}
          </span>
        </div>
      )}
      {error && <ErrorNote text={error} />}
    </AssistantShell>
  )
}
