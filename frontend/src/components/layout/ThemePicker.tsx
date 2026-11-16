import { useState, useRef, useEffect } from 'react'
import { Palette } from 'lucide-react'
import { useUi, type Theme } from '@/store/ui'
import { cn } from '@/lib/utils'

const THEMES: { id: Theme; label: string; bg: string; sidebar: string; accent: string; light: boolean }[] = [
  { id: 'light',         label: 'Light',          bg: '#f4f3f0', sidebar: '#eceae6', accent: '#e3150e',                        light: true  },
  { id: 'dark',          label: 'Dark',            bg: '#090b10', sidebar: '#12151d', accent: '#a78bfa',                        light: false },
  { id: 'system',        label: 'System',          bg: 'linear-gradient(135deg, #f4f3f0 50%, #090b10 50%)', sidebar: '#eceae6', accent: '#e3150e', light: true },
  { id: 'emerald',       label: 'Emerald',         bg: '#0b1410', sidebar: 'rgb(28 46 36 / 0.9)', accent: '#6ee7a0',           light: false },
  { id: 'ember',         label: 'Ember',           bg: '#161618', sidebar: '#232326', accent: 'linear-gradient(90deg,#f45d2c,#e0322b)', light: false },
  { id: 'tide',          label: 'Tide',            bg: '#11171b', sidebar: '#1b242a', accent: '#6ba3bf',                       light: false },
  { id: 'emerald-light', label: 'Emerald Light',   bg: '#f0f7f2', sidebar: '#e5f0e8', accent: '#0d9e56',                      light: true  },
  { id: 'ember-light',   label: 'Ember Light',     bg: '#f7f4f2', sidebar: '#ede9e6', accent: '#d94a1a',                      light: true  },
  { id: 'tide-light',    label: 'Tide Light',      bg: '#f0f5f8', sidebar: '#e3ecf1', accent: '#2a7fa8',                      light: true  },
]

export function ThemePicker({ className }: { className?: string }) {
  const { theme, setTheme } = useUi()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  return (
    <div ref={ref} className={cn('relative', className)}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="ring-focus glass flex size-9 items-center justify-center rounded-xl border border-line text-fg-2 transition hover:text-fg"
        aria-label="Change theme"
        title="Theme"
      >
        <Palette className="size-4" />
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-64 overflow-hidden rounded-2xl border border-line bg-panel shadow-soft backdrop-blur-xl">
          <div className="p-2">
            <p className="mb-2 px-2 text-[11px] font-semibold uppercase tracking-wider text-muted">Theme</p>
            <div className="grid grid-cols-3 gap-1.5">
              {THEMES.map((t) => (
                <button
                  key={t.id}
                  onClick={() => { setTheme(t.id); setOpen(false) }}
                  title={t.label}
                  className={cn(
                    'group flex flex-col items-center gap-1.5 rounded-xl p-1.5 transition',
                    theme === t.id ? 'ring-2 ring-accent/60 bg-accent-soft' : 'hover:bg-elev-2',
                  )}
                >
                  {/* Mini preview */}
                  <div
                    className="flex h-10 w-full gap-1 rounded-lg overflow-hidden p-1"
                    style={{ background: t.bg }}
                  >
                    <div
                      className="w-1/3 rounded-sm"
                      style={{ background: t.sidebar }}
                    />
                    <div className="flex flex-1 flex-col justify-end gap-0.5">
                      <div
                        className="h-1.5 w-3/4 rounded-full"
                        style={{ background: t.id === 'ember' ? 'linear-gradient(90deg,#f45d2c,#e0322b)' : t.accent }}
                      />
                      <div
                        className="h-1 w-1/2 rounded-full opacity-30"
                        style={{ background: t.light ? '#000' : '#fff' }}
                      />
                    </div>
                  </div>
                  <span className={cn('text-[11px] font-medium leading-tight text-center', theme === t.id ? 'text-fg' : 'text-muted group-hover:text-fg')}>
                    {t.label}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
