import clsx from 'clsx'

/** Tonal "container" colours (Material 3 & Apple HIG) with crisp borders and optimal contrast. */
const TONES = {
  blue: 'bg-blue-500/10 text-blue-700 border-blue-500/20 dark:bg-blue-500/15 dark:text-blue-300 dark:border-blue-400/25',
  violet: 'bg-violet-500/10 text-violet-700 border-violet-500/20 dark:bg-violet-500/15 dark:text-violet-300 dark:border-violet-400/25',
  amber: 'bg-amber-500/10 text-amber-800 border-amber-500/25 dark:bg-amber-500/15 dark:text-amber-300 dark:border-amber-400/30',
  green: 'bg-emerald-500/10 text-emerald-700 border-emerald-500/20 dark:bg-emerald-500/15 dark:text-emerald-300 dark:border-emerald-400/25',
  red: 'bg-red-500/10 text-red-700 border-red-500/20 dark:bg-red-500/15 dark:text-red-300 dark:border-red-400/25',
  grey: 'bg-elev-2/70 text-fg-2 border-line dark:bg-elev-2/40 dark:text-fg-3 dark:border-line',
} as const

const STATUS: Record<string, { tone: keyof typeof TONES; label: string }> = {
  NEW: { tone: 'blue', label: 'New' },
  INVESTIGATING: { tone: 'violet', label: 'Investigating' },
  INVESTIGATED: { tone: 'amber', label: 'Root cause found' },
  FIXING: { tone: 'violet', label: 'Writing fix' },
  PR_OPENED: { tone: 'green', label: 'Fix PR opened' },
  FAILED: { tone: 'red', label: 'Failed' },
  RESOLVED: { tone: 'grey', label: 'Resolved' },
  IGNORED: { tone: 'grey', label: 'Ignored' },
  RUNNING: { tone: 'violet', label: 'Running' },
  COMPLETED: { tone: 'green', label: 'Completed' },
  NO_CHANGE: { tone: 'grey', label: 'No change' },
  REJECTED: { tone: 'red', label: 'Rejected' },
}

export function statusLabel(status: string) {
  return STATUS[status]?.label ?? status.replace('_', ' ').toLowerCase()
}

export function StatusBadge({ status }: { status: string }) {
  const s = STATUS[status] ?? { tone: 'grey' as const, label: status }
  const live = status === 'INVESTIGATING' || status === 'FIXING' || status === 'RUNNING'
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-[11px] font-medium tracking-tight shadow-2xs',
        TONES[s.tone],
      )}
    >
      {live ? (
        <span className="relative flex size-1.5">
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-current opacity-75" />
          <span className="relative inline-flex size-1.5 rounded-full bg-current" />
        </span>
      ) : (
        <span className="size-1.5 rounded-full bg-current opacity-70" />
      )}
      {s.label}
    </span>
  )
}

const SEVERITY_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  CRITICAL: {
    bg: 'bg-red-500/15 dark:bg-red-500/20 text-red-700 dark:text-red-300',
    text: 'text-red-700 dark:text-red-300',
    border: 'border-red-500/30',
  },
  HIGH: {
    bg: 'bg-amber-500/15 dark:bg-amber-500/20 text-amber-800 dark:text-amber-300',
    text: 'text-amber-800 dark:text-amber-300',
    border: 'border-amber-500/30',
  },
  MEDIUM: {
    bg: 'bg-blue-500/10 dark:bg-blue-500/15 text-blue-700 dark:text-blue-300',
    text: 'text-blue-700 dark:text-blue-300',
    border: 'border-blue-500/25',
  },
  LOW: {
    bg: 'bg-elev-2/80 text-fg-3',
    text: 'text-fg-3',
    border: 'border-line',
  },
}

export const SEVERITY_BAR: Record<string, string> = {
  CRITICAL: 'bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.5)]',
  HIGH: 'bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.4)]',
  MEDIUM: 'bg-blue-500',
  LOW: 'bg-fg-3/40',
}

export function SeverityBadge({ severity }: { severity: string | null }) {
  if (!severity) return null
  const style = SEVERITY_STYLES[severity] ?? { bg: 'bg-elev-2 text-fg-3', text: 'text-fg-3', border: 'border-line' }
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-md border px-1.5 py-0.5 font-mono text-[10px] font-semibold tracking-wider uppercase shadow-2xs',
        style.bg,
        style.border,
      )}
    >
      {severity}
    </span>
  )
}

export function ConfidenceBadge({ confidence }: { confidence: string | null }) {
  if (!confidence) return null
  const tone = confidence === 'HIGH' ? TONES.green : confidence === 'MEDIUM' ? TONES.amber : TONES.grey
  return (
    <span className={clsx('inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[11px] font-medium tracking-tight', tone)}>
      <span className="size-1 rounded-full bg-current opacity-80" />
      {confidence.toLowerCase()} confidence
    </span>
  )
}
