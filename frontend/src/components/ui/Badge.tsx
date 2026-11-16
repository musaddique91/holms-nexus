import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import type { ConnStatus } from '@/lib/types'

export function Badge({ children, className, tone = 'neutral' }: {
  children: ReactNode
  className?: string
  tone?: 'neutral' | 'accent' | 'ok' | 'warn' | 'err'
}) {
  const tones = {
    neutral: 'bg-elev-2 text-fg-2 border-line',
    accent: 'bg-accent-soft text-accent border-accent/20',
    ok: 'bg-ok/10 text-ok border-ok/20',
    warn: 'bg-warn/10 text-warn border-warn/20',
    err: 'bg-err/10 text-err border-err/20',
  }
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[11px] font-medium', tones[tone], className)}>
      {children}
    </span>
  )
}

export const statusMeta: Record<ConnStatus, { label: string; dot: string; text: string }> = {
  CONNECTED: { label: 'Connected', dot: 'bg-ok', text: 'text-ok' },
  CONNECTING: { label: 'Connecting', dot: 'bg-warn animate-pulse', text: 'text-warn' },
  ERROR: { label: 'Error', dot: 'bg-err', text: 'text-err' },
  DISCONNECTED: { label: 'Disabled', dot: 'bg-muted/50', text: 'text-muted' },
}

export function StatusDot({ status, className }: { status: ConnStatus; className?: string }) {
  return (
    <span className={cn('relative inline-flex size-2 shrink-0', className)}>
      {status === 'CONNECTED' && <span className="absolute inset-0 animate-ping rounded-full bg-ok opacity-30 [animation-duration:2.5s]" />}
      <span className={cn('relative inline-flex size-2 rounded-full', statusMeta[status].dot)} />
    </span>
  )
}

export function Kbd({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <kbd className={cn('inline-flex h-5 min-w-5 items-center justify-center rounded-md border border-line-strong bg-elev-2 px-1 font-sans text-[10px] font-medium text-muted', className)}>
      {children}
    </kbd>
  )
}
