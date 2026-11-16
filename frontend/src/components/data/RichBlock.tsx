import { useMemo } from 'react'
import { BarChart3, Table2 } from 'lucide-react'
import { fromLegacy, type HOptions } from '@/lib/highcharts'
import { HighchartsBlock } from './HighchartsBlock'
import { DataTable, normalizeTable } from './DataTable'

/** Renders model-emitted ```highcharts / ```chart / ```datatable blocks; shows a skeleton while JSON streams in. */
export function RichBlock({ lang, source, streaming }: { lang: 'highcharts' | 'chart' | 'datatable'; source: string; streaming?: boolean }) {
  const parsed = useMemo(() => {
    try {
      return JSON.parse(source)
    } catch {
      return null
    }
  }, [source])

  if (parsed && lang === 'datatable') {
    const spec = normalizeTable(parsed)
    if (spec) return <DataTable spec={spec} />
  }
  if (parsed && lang !== 'datatable') {
    const options: HOptions = lang === 'chart' ? fromLegacy(parsed) : parsed
    if (Array.isArray(options.series) && options.series.length) return <HighchartsBlock options={options} />
  }
  const Icon = lang === 'datatable' ? Table2 : BarChart3
  return (
    <div className="not-prose flex h-48 flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-line-strong bg-elev/40 text-sm text-muted">
      <Icon className="size-6 text-accent" />
      {streaming ? (
        <span className="shimmer-text">{lang === 'datatable' ? 'Building table…' : 'Building chart…'}</span>
      ) : (
        <span>Could not render this {lang === 'datatable' ? 'table' : 'chart'}</span>
      )}
    </div>
  )
}
