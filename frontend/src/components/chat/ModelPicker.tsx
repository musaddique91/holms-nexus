import { Check, ChevronDown, Cpu, KeyRound } from 'lucide-react'
import type { Provider } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Popover } from '@/components/ui/Popover'
import { useUi } from '@/store/ui'
import { ProviderGlyph } from '@/components/settings/ProviderGlyph'

export function ModelPicker({ providers, providerId, model, onChange }: {
  providers: Provider[]
  providerId: string | null
  model: string | null
  onChange: (providerId: string, model: string) => void
}) {
  const { openSettings } = useUi()
  const usable = providers.filter((p) => p.enabled)
  const current = usable.find((p) => p.id === providerId)
  return (
    <Popover
      side="top"
      align="end"
      className="w-72"
      trigger={({ toggle, open }) => (
        <button
          type="button"
          onClick={toggle}
          className={cn(
            'ring-focus flex h-8 max-w-40 items-center sm:max-w-56 gap-1.5 rounded-full px-2.5 text-[13px] transition',
            open ? 'bg-elev-2 text-fg' : 'text-fg-2 hover:bg-elev-2 hover:text-fg',
          )}
        >
          {current ? <ProviderGlyph type={current.type} className="size-3.5" /> : <Cpu className="size-3.5" />}
          <span className="truncate">{model || 'Select model'}</span>
          <ChevronDown className="size-3.5 shrink-0 text-muted" />
        </button>
      )}
    >
      {(close) => (
        <div className="max-h-96 overflow-y-auto">
          {usable.map((p) => (
            <div key={p.id} className="py-1">
              <div className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold tracking-wider text-muted uppercase">
                <ProviderGlyph type={p.type} className="size-3.5" /> {p.name}
                {!p.ready && <span className="ml-auto flex items-center gap-1 font-normal normal-case text-warn"><KeyRound className="size-3" /> needs key</span>}
              </div>
              {(p.models.length ? p.models : [p.defaultModel]).map((m) => {
                const on = p.id === providerId && m === model
                return (
                  <button
                    key={m}
                    disabled={!p.ready}
                    onClick={() => { onChange(p.id, m); close() }}
                    className="flex w-full items-center gap-2 rounded-xl px-2.5 py-1.5 text-left font-mono text-[13px] transition hover:bg-elev-2 disabled:opacity-40 disabled:hover:bg-transparent"
                  >
                    <span className="flex-1 truncate">{m}</span>
                    {on && <Check className="size-4 text-accent" />}
                  </button>
                )
              })}
            </div>
          ))}
          <button
            onClick={() => { close(); openSettings('models') }}
            className="mt-1 flex w-full items-center gap-2 rounded-xl border-t border-line px-2.5 py-2 text-sm text-fg-2 transition hover:bg-elev-2 hover:text-fg"
          >
            <KeyRound className="size-4" /> Manage providers & keys
          </button>
        </div>
      )}
    </Popover>
  )
}
