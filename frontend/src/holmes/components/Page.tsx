import type { ReactNode } from 'react'

export function PageHeader({
  title,
  subtitle,
  actions,
  badge,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
  badge?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4 border-b border-line/50 pb-5">
      <div className="space-y-1">
        <div className="flex items-center gap-2.5">
          <h1 className="text-2xl font-bold tracking-tight text-fg md:text-3xl">{title}</h1>
          {badge}
        </div>
        {subtitle && <p className="max-w-2xl text-sm text-fg-3">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2.5">{actions}</div>}
    </div>
  )
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  return (
    <div
      role="alert"
      className="mb-4 flex items-center gap-2.5 rounded-xl border border-err/25 bg-err/10 px-4 py-3 text-sm font-medium text-err"
    >
      <div className="size-2 animate-pulse rounded-full bg-err" />
      <span>{error instanceof Error ? error.message : String(error)}</span>
    </div>
  )
}
