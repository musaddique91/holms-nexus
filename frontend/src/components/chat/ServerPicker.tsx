import { Check, Plug, Plus, Settings2 } from 'lucide-react'
import type { McpServer } from '@/lib/types'
import { cn } from '@/lib/utils'
import { Popover } from '@/components/ui/Popover'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { StatusDot } from '@/components/ui/Badge'
import { useUi } from '@/store/ui'

export function ServerPicker({ servers, selected, onChange }: {
  servers: McpServer[]
  selected: string[]
  onChange: (ids: string[]) => void
}) {
  const { openSettings, setAddServerOpen } = useUi()
  const enabled = servers.filter((s) => s.enabled)
  const active = enabled.filter((s) => selected.includes(s.id))
  const toolCount = active.reduce((n, s) => n + (s.status === 'CONNECTED' ? s.toolCount : 0), 0)

  return (
    <Popover
      side="top"
      className="w-80"
      trigger={({ toggle, open }) => (
        <button
          type="button"
          onClick={toggle}
          className={cn(
            'ring-focus flex h-8 items-center gap-2 rounded-full border px-2.5 text-[13px] whitespace-nowrap transition',
            open ? 'border-line-strong bg-elev-2 text-fg' : 'border-line text-fg-2 hover:border-line-strong hover:text-fg',
          )}
        >
          {active.length ? (
            <span className="flex -space-x-1.5">
              {active.slice(0, 3).map((s) => (
                <ServerIcon key={s.id} name={s.name} icon={s.icon} color={s.color} size="xs" className="ring-2 ring-elev" />
              ))}
            </span>
          ) : (
            <Plug className="size-3.5" />
          )}
          <span>{active.length ? `${active.length} source${active.length > 1 ? 's' : ''}` : 'No sources'}</span>
          {active.length > 0 && <span className="hidden text-muted sm:inline">· {toolCount} tools</span>}
        </button>
      )}
    >
      {(close) => (
        <div>
          <div className="flex items-center justify-between px-2.5 pt-1.5 pb-2">
            <span className="text-xs font-semibold tracking-wider text-muted uppercase">Data sources</span>
            {enabled.length > 0 && (
              <button
                className="text-xs font-medium text-accent hover:underline"
                onClick={() => onChange(active.length === enabled.length ? [] : enabled.map((s) => s.id))}
              >
                {active.length === enabled.length ? 'Disable all' : 'Enable all'}
              </button>
            )}
          </div>
          <div className="max-h-72 space-y-0.5 overflow-y-auto">
            {enabled.length === 0 && (
              <p className="px-2.5 py-3 text-sm text-muted">No MCP servers yet. Connect one to let MVX MCP query your data.</p>
            )}
            {enabled.map((s) => {
              const on = selected.includes(s.id)
              return (
                <button
                  key={s.id}
                  onClick={() => onChange(on ? selected.filter((x) => x !== s.id) : [...selected, s.id])}
                  className="flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left transition hover:bg-elev-2"
                >
                  <ServerIcon name={s.name} icon={s.icon} color={s.color} size="sm" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{s.name}</span>
                    <span className="flex items-center gap-1.5 text-xs text-muted">
                      <StatusDot status={s.status} />
                      {s.status === 'CONNECTED' ? `${s.toolCount} tools` : s.status === 'ERROR' ? 'Connection failed' : 'Connecting…'}
                    </span>
                  </span>
                  <span
                    className={cn(
                      'flex size-5 items-center justify-center rounded-md border transition',
                      on ? 'border-transparent bg-gradient-accent text-white' : 'border-line-strong',
                    )}
                  >
                    {on && <Check className="size-3.5" strokeWidth={3} />}
                  </span>
                </button>
              )
            })}
          </div>
          <div className="mt-1.5 flex gap-1 border-t border-line pt-1.5">
            <button
              onClick={() => { close(); openSettings('servers'); setAddServerOpen(true) }}
              className="flex flex-1 items-center gap-2 rounded-xl px-2.5 py-2 text-sm text-fg-2 transition hover:bg-elev-2 hover:text-fg"
            >
              <Plus className="size-4" /> Add server
            </button>
            <button
              onClick={() => { close(); openSettings('servers') }}
              className="flex flex-1 items-center gap-2 rounded-xl px-2.5 py-2 text-sm text-fg-2 transition hover:bg-elev-2 hover:text-fg"
            >
              <Settings2 className="size-4" /> Manage
            </button>
          </div>
        </div>
      )}
    </Popover>
  )
}
