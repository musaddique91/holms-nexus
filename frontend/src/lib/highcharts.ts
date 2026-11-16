/**
 * Highcharts engine: one shared instance with every module the agents may use, app-matched theming,
 * chart-type switching, 3D and client-side export (PNG / JPEG / SVG / PDF / CSV / XLS).
 */
import Highcharts from 'highcharts/esm/highcharts.js'
import 'highcharts/esm/highcharts-more.js'
import 'highcharts/esm/highcharts-3d.js'
import 'highcharts/esm/modules/exporting.js'
import 'highcharts/esm/modules/offline-exporting.js'
import 'highcharts/esm/modules/export-data.js'
import 'highcharts/esm/modules/heatmap.js'
import 'highcharts/esm/modules/treemap.js'
import 'highcharts/esm/modules/sunburst.js'
import 'highcharts/esm/modules/sankey.js'
import 'highcharts/esm/modules/dependency-wheel.js'
import 'highcharts/esm/modules/funnel.js'
import 'highcharts/esm/modules/solid-gauge.js'
import 'highcharts/esm/modules/wordcloud.js'
import 'highcharts/esm/modules/xrange.js'
import 'highcharts/esm/modules/variwide.js'
import 'highcharts/esm/modules/streamgraph.js'
import 'highcharts/esm/modules/item-series.js'
import 'highcharts/esm/modules/drilldown.js'
import 'highcharts/esm/modules/no-data-to-display.js'

export { Highcharts }
export type HOptions = Highcharts.Options
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Any = any

const DARK_COLORS = ['#a78bfa', '#22d3ee', '#34d399', '#fbbf24', '#f472b6', '#60a5fa', '#fb923c', '#f87171']
const LIGHT_COLORS = ['#e3150e', '#141110', '#f2766f', '#8a817c', '#b40c07', '#c9c2bd', '#ff9f43', '#5c5552']
const EMERALD_COLORS = ['#6ee7a0', '#a3e635', '#34d399', '#fbbf24', '#22d3ee', '#93a99b', '#f87171', '#60a5fa']
const EMBER_COLORS = ['#f45d2c', '#9ca3af', '#fb923c', '#d4d4d6', '#e0322b', '#6b7280', '#fbbf24', '#a1a1a6']
const TIDE_COLORS = ['#6ba3bf', '#9cc4d4', '#34d399', '#fbbf24', '#4f86a1', '#a78bfa', '#f87171', '#8fa0aa']

export const isDark = () => document.documentElement.classList.contains('dark')

export const activeTheme = (): string => {
  const cl = document.documentElement.classList
  if (cl.contains('theme-emerald')) return 'emerald'
  if (cl.contains('theme-ember')) return 'ember'
  if (cl.contains('theme-tide')) return 'tide'
  return isDark() ? 'dark' : 'light'
}

/** Theme derived from the app's CSS tokens (Highcharts needs concrete colours, not var()). */
export function themeOptions(): HOptions {
  const cs = getComputedStyle(document.documentElement)
  const v = (n: string) => cs.getPropertyValue(n).trim()
  const fg = v('--fg'), fg2 = v('--fg-2'), muted = v('--muted'), line = v('--line'), lineStrong = v('--line-strong')
  const elev = v('--elev')
  const axis = {
    lineColor: lineStrong,
    tickColor: lineStrong,
    gridLineColor: line,
    labels: { style: { color: muted, fontSize: '11px' } },
    title: { style: { color: muted, fontWeight: '500' } },
  }
  return {
    colors: (() => {
      const t = activeTheme()
      if (t === 'emerald') return EMERALD_COLORS
      if (t === 'ember') return EMBER_COLORS
      if (t === 'tide') return TIDE_COLORS
      return isDark() ? DARK_COLORS : LIGHT_COLORS
    })(),
    chart: {
      backgroundColor: 'transparent',
      style: { fontFamily: "'Inter Variable', ui-sans-serif, system-ui, sans-serif" },
      spacing: [14, 10, 10, 10],
    },
    title: { align: 'left', style: { color: fg, fontSize: '15px', fontWeight: '600' } },
    subtitle: { align: 'left', style: { color: muted, fontSize: '12px' } },
    xAxis: axis,
    yAxis: { ...axis, gridLineDashStyle: 'Dash' },
    legend: {
      itemStyle: { color: fg2, fontWeight: '500', fontSize: '12px' },
      itemHoverStyle: { color: fg },
      itemHiddenStyle: { color: muted },
    },
    tooltip: {
      backgroundColor: elev,
      borderColor: lineStrong,
      borderRadius: 10,
      shadow: false,
      style: { color: fg, fontSize: '12px' },
    },
    plotOptions: {
      series: { animation: { duration: 650 }, dataLabels: { style: { color: fg, textOutline: 'none', fontWeight: '500' } } },
      column: { borderRadius: 4, borderWidth: 0 },
      bar: { borderRadius: 4, borderWidth: 0 },
      pie: { borderWidth: 2, borderColor: elev, dataLabels: { style: { color: fg, textOutline: 'none' } } },
      area: { fillOpacity: 0.25 },
      areaspline: { fillOpacity: 0.25 },
    } as Any,
    credits: { enabled: false },
    // Exports are client-side (PDF via jsPDF in exportPdf); libURL only silences Highcharts' warning.
    exporting: { enabled: false, fallbackToExportServer: false, libURL: '/' } as Any,
    accessibility: { enabled: false },
    lang: { noData: 'No data to display' },
  }
}

/** Strip anything that could carry code from model-generated options. */
const UNSAFE = new Set(['events', 'formatter', 'pointFormatter', 'labelFormatter', 'positioner', 'exporting', 'credits'])
export function sanitize<T>(value: T): T {
  if (Array.isArray(value)) return value.map(sanitize) as T
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (UNSAFE.has(k) || typeof v === 'function') continue
      if (k === 'useHTML') continue
      out[k] = sanitize(v)
    }
    return out as T
  }
  return value
}

// ---------------------------------------------------------------- legacy ```chart spec

interface LegacySpec {
  type?: string
  title?: string
  xKey?: string
  series?: { key: string; name?: string }[]
  data?: Record<string, unknown>[]
}

/** Converts the earlier simple `chart` block format into Highcharts options. */
export function fromLegacy(spec: LegacySpec): HOptions {
  const data = spec.data ?? []
  const xKey = spec.xKey || Object.keys(data[0] ?? {})[0]
  const series = spec.series?.length
    ? spec.series
    : Object.keys(data[0] ?? {}).filter((k) => k !== xKey && typeof data[0][k] === 'number').map((key) => ({ key, name: key }))
  const type = spec.type === 'bar' ? 'column' : spec.type || 'column'
  if (type === 'pie') {
    return {
      chart: { type: 'pie' },
      title: { text: spec.title },
      series: [{ type: 'pie', name: series[0]?.name ?? '', data: data.map((d) => ({ name: String(d[xKey]), y: Number(d[series[0]?.key]) })) }],
    } as HOptions
  }
  return {
    chart: { type },
    title: { text: spec.title },
    xAxis: { categories: data.map((d) => String(d[xKey])) },
    yAxis: { title: { text: undefined } },
    series: series.map((s) => ({ type, name: s.name ?? s.key, data: data.map((d) => Number(d[s.key])) })),
  } as HOptions
}

// ---------------------------------------------------------------- type switching

export type ViewType =
  | 'original' | 'column' | 'bar' | 'line' | 'spline' | 'area' | 'areaspline'
  | 'column-stacked' | 'bar-stacked' | 'area-stacked' | 'column-percent' | 'pie' | 'donut' | 'scatter'

export const VIEW_TYPES: { id: ViewType; label: string; group: string }[] = [
  { id: 'original', label: 'Original', group: 'Default' },
  { id: 'column', label: 'Column', group: 'Compare' },
  { id: 'bar', label: 'Bar', group: 'Compare' },
  { id: 'column-stacked', label: 'Stacked column', group: 'Compare' },
  { id: 'bar-stacked', label: 'Stacked bar', group: 'Compare' },
  { id: 'column-percent', label: '100% column', group: 'Compare' },
  { id: 'line', label: 'Line', group: 'Trend' },
  { id: 'spline', label: 'Spline', group: 'Trend' },
  { id: 'area', label: 'Area', group: 'Trend' },
  { id: 'areaspline', label: 'Area spline', group: 'Trend' },
  { id: 'area-stacked', label: 'Stacked area', group: 'Trend' },
  { id: 'pie', label: 'Pie', group: 'Composition' },
  { id: 'donut', label: 'Donut', group: 'Composition' },
  { id: 'scatter', label: 'Scatter', group: 'Correlation' },
]

const SWITCHABLE = new Set(['line', 'spline', 'area', 'areaspline', 'column', 'bar', 'pie', 'scatter'])
const THREE_D = new Set(['column', 'bar', 'pie', 'scatter', 'area', 'line', 'spline', 'areaspline'])

export function baseType(o: HOptions): string {
  return String((o.chart as Any)?.type ?? (o.series as Any)?.[0]?.type ?? 'line')
}

/** Simple category/pie charts can be re-typed; specialised ones (sankey, heatmap…) keep their type. */
export function canSwitch(o: HOptions): boolean {
  const series = (o.series ?? []) as Any[]
  return SWITCHABLE.has(baseType(o)) && series.every((s) => !s.type || SWITCHABLE.has(s.type)) && !(o.yAxis instanceof Array && o.yAxis.length > 1)
}

export function supports3d(o: HOptions, view: ViewType): boolean {
  const t = view === 'original' ? baseType(o) : view.split('-')[0] === 'donut' ? 'pie' : view.split('-')[0]
  return THREE_D.has(t)
}

const isPieLike = (o: HOptions) => baseType(o) === 'pie'

function toPie(o: HOptions): HOptions {
  const categories = ((o.xAxis as Any)?.categories ?? []) as string[]
  const first = ((o.series ?? []) as Any[])[0] ?? { data: [] }
  const data = (first.data as Any[]).map((p: Any, i: number) =>
    typeof p === 'number' ? { name: categories[i] ?? `#${i + 1}`, y: p }
      : Array.isArray(p) ? { name: String(categories[i] ?? p[0]), y: Number(p[1]) }
        : { name: p.name ?? categories[i], y: p.y },
  )
  return { ...o, xAxis: undefined, yAxis: undefined, series: [{ type: 'pie', name: first.name, data }] as Any }
}

function fromPie(o: HOptions): HOptions {
  const first = ((o.series ?? []) as Any[])[0] ?? { data: [] }
  const points = (first.data as Any[]).map((p: Any) => (Array.isArray(p) ? { name: p[0], y: p[1] } : p))
  return {
    ...o,
    xAxis: { categories: points.map((p: Any) => String(p.name)) },
    yAxis: { title: { text: undefined } },
    series: [{ name: first.name ?? 'Value', data: points.map((p: Any) => p.y) }] as Any,
  }
}

/** Builds the options to render for a chosen view type and 3D setting. */
export function buildOptions(original: HOptions, view: ViewType, threeD: boolean): HOptions {
  let o: HOptions = JSON.parse(JSON.stringify(original))
  if (view !== 'original') {
    const [kind, mod] = view.split('-') as [string, string | undefined]
    const target = kind === 'donut' ? 'pie' : kind
    if (target === 'pie' && !isPieLike(o)) o = toPie(o)
    if (target !== 'pie' && isPieLike(o)) o = fromPie(o)
    o.series = ((o.series ?? []) as Any[]).map((s) => ({ ...s, type: target })) as Any
    o.chart = { ...(o.chart ?? {}), type: target } as Any
    const plot = { ...(o.plotOptions ?? {}) } as Any
    plot.series = { ...(plot.series ?? {}), stacking: mod === 'stacked' ? 'normal' : mod === 'percent' ? 'percent' : undefined }
    if (target === 'pie') plot.pie = { ...(plot.pie ?? {}), innerSize: kind === 'donut' ? '58%' : 0, showInLegend: true }
    o.plotOptions = plot
  }
  // Heatmaps/treemaps: theme the colour axis only when the chart actually defines one.
  if (o.colorAxis && !Array.isArray(o.colorAxis)) {
    const cs = getComputedStyle(document.documentElement)
    const accentColor = cs.getPropertyValue('--accent').trim() || (isDark() ? '#a78bfa' : '#e3150e')
    const minColor = isDark() ? '#1e1b2e' : '#fde8e7'
    o.colorAxis = { minColor, maxColor: accentColor, ...(o.colorAxis as Any) }
  }
  if (threeD && supports3d(original, view)) {
    const t = baseType(o)
    o.chart = {
      ...(o.chart ?? {}),
      options3d: { enabled: true, alpha: t === 'pie' ? 45 : 14, beta: t === 'pie' ? 0 : 16, depth: t === 'pie' ? 50 : 60, viewDistance: 28 },
    } as Any
    const plot = { ...(o.plotOptions ?? {}) } as Any
    plot.pie = { ...(plot.pie ?? {}), depth: 45 }
    plot.column = { ...(plot.column ?? {}), depth: 32 }
    plot.bar = { ...(plot.bar ?? {}), depth: 32 }
    plot.area = { ...(plot.area ?? {}), depth: 40 }
    o.plotOptions = plot
  } else if ((o.chart as Any)?.options3d) {
    ;(o.chart as Any).options3d = { enabled: false }
  }
  return o
}

// ---------------------------------------------------------------- export

const safeName = (s: string) => (s || 'chart').replace(/[^\w\- ]+/g, '').trim().replace(/\s+/g, '-').toLowerCase() || 'chart'

export async function exportImage(chart: Highcharts.Chart, type: 'image/png' | 'image/jpeg' | 'image/svg+xml', title: string) {
  await (chart as Any).exporting.exportChart({ type, filename: safeName(title), scale: 2 }, { chart: { backgroundColor: exportBg() } })
}

const exportBg = () => getComputedStyle(document.documentElement).getPropertyValue('--elev').trim() || '#ffffff'

/** PDF via SVG → high-res PNG → jsPDF (fully offline; no Highcharts export server). */
export async function exportPdf(chart: Highcharts.Chart, title: string) {
  const svg: string = await (chart as Any).exporting.getSVGForExport({}, { chart: { backgroundColor: exportBg() } })
  const width = chart.chartWidth, height = chart.chartHeight, scale = 2
  const img = new Image()
  const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }))
  await new Promise<void>((resolve, reject) => {
    img.onload = () => resolve()
    img.onerror = () => reject(new Error('Could not rasterise chart'))
    img.src = url
  })
  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = exportBg()
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
  URL.revokeObjectURL(url)
  const { jsPDF } = await import('jspdf')
  const pdf = new jsPDF({ orientation: width >= height ? 'landscape' : 'portrait', unit: 'pt', format: [width + 48, height + 72] })
  pdf.setFontSize(10)
  pdf.setTextColor(120)
  pdf.text(`${title} — exported from holms-nexus ${new Date().toLocaleString()}`, 24, 28)
  pdf.addImage(canvas.toDataURL('image/png'), 'PNG', 24, 44, width, height)
  pdf.save(`${safeName(title)}.pdf`)
}

export function exportCsv(chart: Highcharts.Chart) {
  ;(chart as Any).downloadCSV()
}

export function exportXls(chart: Highcharts.Chart) {
  ;(chart as Any).downloadXLS()
}

/** Rows (first row = headers) for the "view data" table. */
export function dataRows(chart: Highcharts.Chart): (string | number)[][] {
  try {
    return (chart as Any).getDataRows() as (string | number)[][]
  } catch {
    return []
  }
}
