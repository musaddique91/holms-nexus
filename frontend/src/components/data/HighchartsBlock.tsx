import { useEffect, useMemo, useRef, useState } from 'react'
import { Box, Braces, Check, ChevronDown, Download, FileImage, FileText, FileSpreadsheet, Maximize2, Minimize2, Rotate3d, Table2, BarChart3 } from 'lucide-react'
import { toast } from 'sonner'
import {
  Highcharts, VIEW_TYPES, baseType, buildOptions, canSwitch, dataRows, exportCsv, exportImage, exportPdf, exportXls,
  sanitize, supports3d, themeOptions, type HOptions, type ViewType,
} from '@/lib/highcharts'
import { cn } from '@/lib/utils'
import { useUi } from '@/store/ui'
import { Popover } from '@/components/ui/Popover'
import { Spinner } from '@/components/ui/Spinner'
import { DataTable } from './DataTable'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Any = any

export function HighchartsBlock({ options: raw, streaming }: { options: HOptions; streaming?: boolean }) {
  const theme = useUi((s) => s.theme)
  const original = useMemo(() => sanitize(raw), [raw])
  const initial3d = !!(original.chart as Any)?.options3d?.enabled
  const [view, setView] = useState<ViewType>('original')
  const [threeD, setThreeD] = useState(initial3d)
  const [showData, setShowData] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [rows, setRows] = useState<(string | number)[][]>([])
  const frame = useRef<HTMLDivElement>(null)
  const host = useRef<HTMLDivElement>(null)
  const chartRef = useRef<Highcharts.Chart | null>(null)
  const title = String((original.title as Any)?.text ?? 'Chart')
  const switchable = canSwitch(original)
  const can3d = supports3d(original, view)

  // (Re)render on any input change. Theme is read from CSS tokens at render time.
  useEffect(() => {
    if (!host.current) return
    const merged = Highcharts.merge(themeOptions(), buildOptions(original, view, threeD && can3d), {
      chart: { height: fullscreen ? Math.max(420, window.innerHeight - 140) : 360 },
    } as HOptions)
    try {
      chartRef.current?.destroy()
      chartRef.current = Highcharts.chart(host.current, merged)
      setRows(dataRows(chartRef.current))
    } catch (e) {
      chartRef.current = null
      console.warn('Chart render failed', e)
    }
    return () => {
      chartRef.current?.destroy()
      chartRef.current = null
    }
  }, [original, view, threeD, can3d, theme, fullscreen])

  // Keep the chart sized to its container.
  useEffect(() => {
    const el = host.current
    if (!el) return
    const ro = new ResizeObserver(() => chartRef.current?.reflow())
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  useEffect(() => {
    const onChange = () => setFullscreen(document.fullscreenElement === frame.current)
    document.addEventListener('fullscreenchange', onChange)
    return () => document.removeEventListener('fullscreenchange', onChange)
  }, [])

  // Drag to rotate in 3D.
  const onPointerDown = (e: React.PointerEvent) => {
    const chart = chartRef.current as Any
    if (!threeD || !chart?.options?.chart?.options3d?.enabled) return
    const start = { x: e.clientX, y: e.clientY, alpha: chart.options.chart.options3d.alpha, beta: chart.options.chart.options3d.beta }
    const move = (ev: PointerEvent) => {
      chart.update(
        { chart: { options3d: { alpha: Math.max(-45, Math.min(60, start.alpha + (ev.clientY - start.y) / 4)), beta: start.beta + (start.x - ev.clientX) / 4 } } },
        undefined, undefined, false,
      )
    }
    const up = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
  }

  const run = async (key: string, fn: (c: Highcharts.Chart) => Promise<void> | void) => {
    const chart = chartRef.current
    if (!chart) return
    setBusy(key)
    try {
      await fn(chart)
    } catch (e) {
      toast.error('Export failed', { description: (e as Error).message })
    } finally {
      setBusy(null)
    }
  }

  const toggleFullscreen = async () => {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await frame.current?.requestFullscreen()
  }

  const currentLabel = view === 'original' ? `${capitalize(baseType(original))}` : VIEW_TYPES.find((v) => v.id === view)?.label
  const table = useMemo(() => {
    if (rows.length < 2) return null
    const headers = rows[0].map((h, i) => String(h || (i === 0 ? 'Category' : `Series ${i}`)))
    return {
      title: `${title} — data`,
      columns: headers.map((h, i) => ({ key: `c${i}`, label: h, type: i === 0 ? ('text' as const) : ('number' as const) })),
      rows: rows.slice(1).map((r) => Object.fromEntries(r.map((v, i) => [`c${i}`, v]))),
    }
  }, [rows, title])

  return (
    <figure ref={frame} className={cn('not-prose overflow-hidden rounded-2xl border border-line bg-elev shadow-soft', fullscreen && 'flex flex-col overflow-auto rounded-none p-4')}>
      <div className="flex flex-wrap items-center gap-1.5 border-b border-line px-3 py-2">
        <span className="mr-auto flex min-w-0 items-center gap-2 pl-1 text-xs text-muted">
          <BarChart3 className="size-3.5 shrink-0 text-accent" />
          <span className="truncate">{streaming ? 'Rendering…' : 'Interactive chart'}</span>
        </span>

        {switchable && (
          <Popover
            side="bottom"
            align="end"
            className="w-56"
            trigger={({ toggle, open }) => (
              <ToolbarButton onClick={toggle} active={open} title="Change chart type">
                <BarChart3 className="size-3.5" /> <span className="hidden sm:inline">{currentLabel}</span> <ChevronDown className="size-3" />
              </ToolbarButton>
            )}
          >
            {(close) => (
              <div className="max-h-80 overflow-y-auto">
                {Array.from(new Set(VIEW_TYPES.map((v) => v.group))).map((group) => (
                  <div key={group} className="py-0.5">
                    <div className="px-2.5 pt-1.5 pb-1 text-[10px] font-semibold tracking-wider text-muted uppercase">{group}</div>
                    {VIEW_TYPES.filter((v) => v.group === group).map((v) => (
                      <button
                        key={v.id}
                        onClick={() => { setView(v.id); close() }}
                        className="flex w-full items-center justify-between rounded-lg px-2.5 py-1.5 text-left text-[13px] text-fg-2 transition hover:bg-elev-2 hover:text-fg"
                      >
                        {v.id === 'original' ? `Original (${capitalize(baseType(original))})` : v.label}
                        {view === v.id && <Check className="size-3.5 text-accent" />}
                      </button>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </Popover>
        )}

        <ToolbarButton onClick={() => setThreeD((t) => !t)} active={threeD && can3d} disabled={!can3d} title={can3d ? 'Toggle 3D (drag to rotate)' : '3D not available for this chart type'}>
          <Rotate3d className="size-3.5" /> 3D
        </ToolbarButton>
        <ToolbarButton onClick={() => setShowData((s) => !s)} active={showData} disabled={!table} title="View data">
          <Table2 className="size-3.5" /> <span className="hidden sm:inline">Data</span>
        </ToolbarButton>

        <Popover
          side="bottom"
          align="end"
          className="w-48"
          trigger={({ toggle, open }) => (
            <ToolbarButton onClick={toggle} active={open} title="Download">
              {busy ? <Spinner className="size-3.5" /> : <Download className="size-3.5" />} <span className="hidden sm:inline">Export</span>
            </ToolbarButton>
          )}
        >
          {(close) => (
            <div>
              <MenuRow icon={FileImage} label="PNG image" onClick={() => { close(); run('png', (c) => exportImage(c, 'image/png', title)) }} />
              <MenuRow icon={FileImage} label="JPEG image" onClick={() => { close(); run('jpeg', (c) => exportImage(c, 'image/jpeg', title)) }} />
              <MenuRow icon={Box} label="SVG vector" onClick={() => { close(); run('svg', (c) => exportImage(c, 'image/svg+xml', title)) }} />
              <MenuRow icon={FileText} label="PDF document" onClick={() => { close(); run('pdf', (c) => exportPdf(c, title)) }} />
              <div className="my-1 border-t border-line" />
              <MenuRow icon={FileSpreadsheet} label="CSV data" onClick={() => { close(); run('csv', exportCsv) }} />
              <MenuRow icon={FileSpreadsheet} label="Excel (XLS)" onClick={() => { close(); run('xls', exportXls) }} />
              <MenuRow icon={Braces} label="Copy chart JSON" onClick={() => { close(); navigator.clipboard.writeText(JSON.stringify(original, null, 2)); toast.success('Chart JSON copied') }} />
            </div>
          )}
        </Popover>

        <ToolbarButton onClick={toggleFullscreen} title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}>
          {fullscreen ? <Minimize2 className="size-3.5" /> : <Maximize2 className="size-3.5" />}
        </ToolbarButton>
      </div>

      <div className={cn('relative px-2 pt-2 pb-1', threeD && can3d && 'cursor-grab active:cursor-grabbing', fullscreen && 'flex-1')} onPointerDown={onPointerDown}>
        <div ref={host} className="w-full" />
        {threeD && can3d && (
          <span className="pointer-events-none absolute right-4 bottom-3 rounded-md bg-elev-2/90 px-2 py-0.5 text-[10px] text-muted">Drag to rotate</span>
        )}
      </div>

      {showData && table && (
        <div className="border-t border-line p-3">
          <DataTable spec={table} compact />
        </div>
      )}
    </figure>
  )
}

function ToolbarButton({ children, onClick, active, disabled, title }: { children: React.ReactNode; onClick: () => void; active?: boolean; disabled?: boolean; title?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={cn(
        'inline-flex h-7 items-center gap-1.5 rounded-lg px-2 text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-35',
        active ? 'bg-accent-soft text-accent' : 'text-fg-2 hover:bg-elev-2 hover:text-fg',
      )}
    >
      {children}
    </button>
  )
}

function MenuRow({ icon: Icon, label, onClick }: { icon: React.ElementType; label: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-left text-[13px] text-fg-2 transition hover:bg-elev-2 hover:text-fg">
      <Icon className="size-4 text-muted" /> {label}
    </button>
  )
}

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)
