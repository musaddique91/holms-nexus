import { useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, ArrowUpDown, BarChart3, ChevronLeft, ChevronRight, Download, Search, Table2, X } from 'lucide-react'
import { cn, colorFor } from '@/lib/utils'
import { CopyButton } from '@/components/chat/CodeBlock'
import { HighchartsBlock } from './HighchartsBlock'
import type { HOptions } from '@/lib/highcharts'

export type ColumnType = 'text' | 'number' | 'currency' | 'percent' | 'date' | 'datetime' | 'badge' | 'progress' | 'link' | 'code'

export interface ColumnSpec {
  key: string
  label?: string
  type?: ColumnType
  currency?: string
  trend?: boolean
  max?: number
}

export interface TableSpec {
  title?: string
  description?: string
  columns?: ColumnSpec[]
  rows: Record<string, unknown>[]
  totals?: string[]
}

const NUMERIC: ColumnType[] = ['number', 'currency', 'percent', 'progress']
const PAGE_SIZES = [10, 25, 50, 100]

function inferType(key: string, rows: Record<string, unknown>[]): ColumnType {
  const vals = rows.map((r) => r[key]).filter((v) => v != null && v !== '')
  if (!vals.length) return 'text'
  if (vals.every((v) => typeof v === 'number')) return /pct|percent|rate|ratio|share/i.test(key) ? 'percent' : /price|revenue|amount|cost|usd|total|sales/i.test(key) ? 'currency' : 'number'
  if (vals.every((v) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}/.test(v))) return String(vals[0]).length > 10 ? 'datetime' : 'date'
  if (vals.every((v) => typeof v === 'string' && /^https?:\/\//.test(v))) return 'link'
  const distinct = new Set(vals.map(String))
  if (vals.length >= 4 && distinct.size <= Math.max(3, vals.length / 3) && [...distinct].every((s) => s.length <= 24)) return 'badge'
  return 'text'
}

const toNum = (v: unknown) => (typeof v === 'number' ? v : typeof v === 'string' && v.trim() !== '' && !isNaN(Number(v)) ? Number(v) : NaN)

/** Normalises any reasonable table payload (rows as objects or arrays) into a spec. */
export function normalizeTable(raw: unknown): TableSpec | null {
  if (!raw || typeof raw !== 'object') return null
  const r = raw as Record<string, unknown>
  let rows = (Array.isArray(raw) ? raw : r.rows ?? r.data) as unknown[] | undefined
  if (!Array.isArray(rows)) return null
  let columns = (r.columns as (ColumnSpec | string)[] | undefined)?.map((c) => (typeof c === 'string' ? { key: c } : c))
  if (rows.length && Array.isArray(rows[0])) {
    const keys = columns?.map((c) => c.key) ?? (rows[0] as unknown[]).map((_, i) => `col${i + 1}`)
    rows = (rows as unknown[][]).map((arr) => Object.fromEntries(keys.map((k, i) => [k, arr[i]])))
  }
  const objRows = rows as Record<string, unknown>[]
  if (!columns?.length) columns = Object.keys(objRows[0] ?? {}).map((key) => ({ key }))
  columns = columns.map((c) => ({ ...c, label: c.label ?? c.key.replace(/[_-]+/g, ' ').replace(/\b\w/g, (m) => m.toUpperCase()), type: c.type ?? inferType(c.key, objRows) }))
  return { title: r.title as string, description: r.description as string, columns, rows: objRows, totals: r.totals as string[] }
}

export function DataTable({ spec: input, compact }: { spec: TableSpec; compact?: boolean }) {
  const spec = useMemo(() => normalizeTable(input) ?? { rows: [], columns: [] }, [input])
  const columns = spec.columns ?? []
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<{ key: string; dir: 1 | -1 } | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(compact ? 10 : 10)
  const [chart, setChart] = useState(false)

  // Percent columns: model may send fractions (0.12) or whole percentages (12).
  const fractions = useMemo(() => {
    const out: Record<string, boolean> = {}
    for (const c of columns) if (c.type === 'percent') out[c.key] = spec.rows.every((r) => Math.abs(toNum(r[c.key]) || 0) <= 1.5)
    return out
  }, [columns, spec.rows])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    let rows = q ? spec.rows.filter((r) => columns.some((c) => String(r[c.key] ?? '').toLowerCase().includes(q))) : spec.rows
    if (sort) {
      const col = columns.find((c) => c.key === sort.key)
      const numeric = col && NUMERIC.includes(col.type!)
      rows = [...rows].sort((a, b) => {
        const x = a[sort.key], y = b[sort.key]
        if (x == null) return 1
        if (y == null) return -1
        return (numeric ? toNum(x) - toNum(y) : String(x).localeCompare(String(y), undefined, { numeric: true })) * sort.dir
      })
    }
    return rows
  }, [spec.rows, columns, query, sort])

  const pages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const current = Math.min(page, pages - 1)
  const visible = filtered.slice(current * pageSize, current * pageSize + pageSize)
  const totals = (spec.totals ?? []).filter((k) => columns.some((c) => c.key === k))

  const csv = useMemo(() => {
    const esc = (v: unknown) => { const s = v == null ? '' : String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s }
    return [columns.map((c) => esc(c.label)).join(','), ...filtered.map((r) => columns.map((c) => esc(r[c.key])).join(','))].join('\n')
  }, [columns, filtered])
  const tsv = useMemo(() => [columns.map((c) => c.label).join('\t'), ...filtered.map((r) => columns.map((c) => r[c.key] ?? '').join('\t'))].join('\n'), [columns, filtered])

  const chartOptions = useMemo<HOptions | null>(() => {
    const label = columns.find((c) => !NUMERIC.includes(c.type!) && c.type !== 'link') ?? columns[0]
    const nums = columns.filter((c) => NUMERIC.includes(c.type!) && c.key !== label?.key)
    if (!label || !nums.length) return null
    const rows = filtered.slice(0, 60)
    return {
      chart: { type: rows.length > 12 ? 'bar' : 'column' },
      title: { text: spec.title ?? 'Table data' },
      xAxis: { categories: rows.map((r) => String(r[label.key] ?? '')) },
      yAxis: { title: { text: undefined } },
      series: nums.slice(0, 4).map((c) => ({ type: rows.length > 12 ? 'bar' : 'column', name: c.label, data: rows.map((r) => toNum(r[c.key]) * (c.type === 'percent' && fractions[c.key] ? 100 : 1)) })),
    } as HOptions
  }, [columns, filtered, spec.title, fractions])

  const download = () => {
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    a.download = `${(spec.title || 'table').replace(/[^\w-]+/g, '-').toLowerCase()}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  const toggleSort = (key: string) => {
    setPage(0)
    setSort((s) => (!s || s.key !== key ? { key, dir: 1 } : s.dir === 1 ? { key, dir: -1 } : null))
  }

  return (
    <div className="not-prose space-y-3">
      <div className="overflow-hidden rounded-2xl border border-line bg-elev shadow-soft">
        <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3">
          <span className="flex size-8 items-center justify-center rounded-lg bg-accent-soft text-accent"><Table2 className="size-4" /></span>
          <div className="mr-auto min-w-0">
            <div className="truncate text-sm font-semibold">{spec.title || 'Data'}</div>
            <div className="truncate text-xs text-muted">
              {spec.description ? `${spec.description} · ` : ''}{filtered.length.toLocaleString()} row{filtered.length === 1 ? '' : 's'}
              {query && ` matching “${query}”`}
            </div>
          </div>
          {spec.rows.length > 5 && (
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted" />
              <input
                value={query}
                onChange={(e) => { setQuery(e.target.value); setPage(0) }}
                placeholder="Filter…"
                className="h-8 w-36 rounded-lg border border-line bg-elev-2 pr-7 pl-8 text-xs outline-none focus:border-accent/50 sm:w-44"
              />
              {query && <button onClick={() => setQuery('')} className="absolute top-1/2 right-2 -translate-y-1/2 text-muted hover:text-fg"><X className="size-3.5" /></button>}
            </div>
          )}
          {chartOptions && (
            <button onClick={() => setChart((c) => !c)} className={cn('inline-flex h-8 items-center gap-1.5 rounded-lg px-2.5 text-xs font-medium transition', chart ? 'bg-accent text-white' : 'border border-line text-fg-2 hover:border-line-strong hover:text-fg')}>
              <BarChart3 className="size-3.5" /> {chart ? 'Hide chart' : 'Chart this'}
            </button>
          )}
          <CopyButton text={tsv} label="Copy" className="h-8 border border-line px-2.5" />
          <button onClick={download} className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-line px-2.5 text-xs text-fg-2 transition hover:border-line-strong hover:text-fg">
            <Download className="size-3.5" /> CSV
          </button>
        </div>
        <div className="max-h-[480px] overflow-auto">
          <table className="w-full border-collapse text-[13px]">
            <thead className="sticky top-0 z-[1] bg-elev-2/95 backdrop-blur">
              <tr>
                {columns.map((c) => {
                  const numeric = NUMERIC.includes(c.type!)
                  const active = sort?.key === c.key
                  return (
                    <th key={c.key} className={cn('border-b border-line-strong px-3.5 py-2.5 text-[11px] font-semibold tracking-wider whitespace-nowrap text-muted uppercase', numeric ? 'text-right' : 'text-left')}>
                      <button onClick={() => toggleSort(c.key)} className={cn('inline-flex items-center gap-1 transition hover:text-fg', active && 'text-fg', numeric && 'flex-row-reverse')}>
                        {c.label}
                        {active ? (sort!.dir === 1 ? <ArrowUp className="size-3 text-accent" /> : <ArrowDown className="size-3 text-accent" />) : <ArrowUpDown className="size-3 opacity-30" />}
                      </button>
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody>
              {visible.map((row, i) => (
                <tr key={i} className="group transition-colors hover:bg-accent-soft">
                  {columns.map((c) => (
                    <td key={c.key} className={cn('border-b border-line px-3.5 py-2.5 align-middle', NUMERIC.includes(c.type!) ? 'text-right tabular-nums' : 'text-left')}>
                      <Cell value={row[c.key]} col={c} fraction={fractions[c.key]} />
                    </td>
                  ))}
                </tr>
              ))}
              {visible.length === 0 && (
                <tr><td colSpan={columns.length} className="px-4 py-10 text-center text-sm text-muted">No rows match.</td></tr>
              )}
            </tbody>
            {totals.length > 0 && (
              <tfoot className="sticky bottom-0 bg-elev-2/95 backdrop-blur">
                <tr>
                  {columns.map((c, i) => (
                    <td key={c.key} className={cn('border-t border-line-strong px-3.5 py-2.5 font-semibold', NUMERIC.includes(c.type!) ? 'text-right tabular-nums' : 'text-left')}>
                      {totals.includes(c.key) ? <Cell value={filtered.reduce((n, r) => n + (toNum(r[c.key]) || 0), 0)} col={{ ...c, trend: false }} fraction={fractions[c.key]} /> : i === 0 ? <span className="text-xs tracking-wider text-muted uppercase">Total</span> : null}
                    </td>
                  ))}
                </tr>
              </tfoot>
            )}
          </table>
        </div>
        {filtered.length > PAGE_SIZES[0] && (
          <div className="flex items-center justify-between gap-3 border-t border-line px-4 py-2 text-xs text-muted">
            <label className="flex items-center gap-2">
              Rows
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0) }} className="rounded-md border border-line bg-elev-2 px-1.5 py-1 text-fg outline-none">
                {PAGE_SIZES.map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <span className="tabular-nums">
              {current * pageSize + 1}–{Math.min(filtered.length, (current + 1) * pageSize)} of {filtered.length.toLocaleString()}
            </span>
            <div className="flex gap-1">
              <button disabled={current === 0} onClick={() => setPage(current - 1)} className="rounded-md border border-line p-1 transition hover:text-fg disabled:opacity-30"><ChevronLeft className="size-4" /></button>
              <button disabled={current >= pages - 1} onClick={() => setPage(current + 1)} className="rounded-md border border-line p-1 transition hover:text-fg disabled:opacity-30"><ChevronRight className="size-4" /></button>
            </div>
          </div>
        )}
      </div>
      {chart && chartOptions && <HighchartsBlock options={chartOptions} />}
    </div>
  )
}

function Cell({ value, col, fraction }: { value: unknown; col: ColumnSpec; fraction?: boolean }) {
  if (value == null || value === '') return <span className="text-muted/50">—</span>
  const n = toNum(value)
  switch (col.type) {
    case 'number':
      return <>{isNaN(n) ? String(value) : n.toLocaleString(undefined, { maximumFractionDigits: 2 })}</>
    case 'currency':
      return <>{isNaN(n) ? String(value) : new Intl.NumberFormat(undefined, { style: 'currency', currency: col.currency || 'USD', maximumFractionDigits: Math.abs(n) >= 1000 ? 0 : 2 }).format(n)}</>
    case 'percent': {
      if (isNaN(n)) return <>{String(value)}</>
      const pct = fraction ? n * 100 : n
      const text = `${pct > 0 && col.trend ? '+' : ''}${pct.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`
      if (!col.trend) return <>{text}</>
      return (
        <span className={cn('inline-flex items-center gap-0.5 font-medium', pct > 0 ? 'text-ok' : pct < 0 ? 'text-err' : 'text-muted')}>
          {pct > 0 ? <ArrowUp className="size-3" /> : pct < 0 ? <ArrowDown className="size-3" /> : null}{text}
        </span>
      )
    }
    case 'date':
    case 'datetime': {
      const d = new Date(String(value))
      if (isNaN(d.getTime())) return <>{String(value)}</>
      return <span className="whitespace-nowrap">{col.type === 'date' ? d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</span>
    }
    case 'badge': {
      const s = String(value)
      const c = badgeColor(s)
      return (
        <span className="inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium whitespace-nowrap" style={{ color: c, background: `color-mix(in oklab, ${c} 10%, transparent)`, borderColor: `color-mix(in oklab, ${c} 25%, transparent)` }}>
          <span className="size-1.5 rounded-full" style={{ background: c }} />{s}
        </span>
      )
    }
    case 'progress': {
      const max = col.max ?? 100
      const ratio = Math.max(0, Math.min(1, n / max))
      return (
        <span className="inline-flex w-full min-w-24 items-center justify-end gap-2">
          <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-elev-2">
            <span className="block h-full rounded-full bg-gradient-accent" style={{ width: `${ratio * 100}%` }} />
          </span>
          <span className="w-9 text-right tabular-nums">{Math.round(ratio * 100)}%</span>
        </span>
      )
    }
    case 'link':
      return <a href={String(value)} target="_blank" rel="noreferrer noopener" className="text-accent hover:underline">{String(value).replace(/^https?:\/\/(www\.)?/, '').slice(0, 48)}</a>
    case 'code':
      return <code className="rounded bg-elev-2 px-1.5 py-0.5 font-mono text-xs">{String(value)}</code>
    default:
      return <span className="break-words">{typeof value === 'object' ? JSON.stringify(value) : String(value)}</span>
  }
}

const STATUS_COLORS: Record<string, string> = {
  active: '#10b981', success: '#10b981', ok: '#10b981', done: '#10b981', completed: '#10b981', healthy: '#10b981', running: '#10b981', open: '#3b82f6',
  pending: '#f59e0b', warning: '#f59e0b', waiting: '#f59e0b', 'in progress': '#3b82f6', draft: '#8a817c',
  error: '#e11d48', failed: '#e11d48', critical: '#e11d48', closed: '#8a817c', inactive: '#8a817c', exited: '#8a817c', blocked: '#e11d48',
}
const badgeColor = (s: string) => STATUS_COLORS[s.toLowerCase()] ?? colorFor(s)
