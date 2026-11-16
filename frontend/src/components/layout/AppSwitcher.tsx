import { useNavigate } from 'react-router-dom'
import { APPS, rememberApp, type AppId } from '@/apps'
import { cn } from '@/lib/utils'

/** Switch between Intelligence and MCP; shown at the top of both sidebars. */
export function AppSwitcher({ current }: { current: AppId }) {
  const navigate = useNavigate()
  return (
    <div className="grid grid-cols-2 gap-1 rounded-xl border border-line bg-elev-2/70 p-1" role="tablist" aria-label="Application">
      {APPS.map((a) => {
        const active = a.id === current
        return (
          <button
            key={a.id}
            role="tab"
            aria-selected={active}
            onClick={() => {
              if (active) return
              rememberApp(a.id)
              navigate(a.path)
            }}
            className={cn(
              'ring-focus flex h-8 items-center justify-center gap-1.5 rounded-lg text-[13px] font-medium transition',
              active ? 'bg-elev text-fg shadow-soft' : 'text-muted hover:text-fg',
            )}
          >
            <a.icon className={cn('size-3.5', active && 'text-accent')} />
            {a.name}
          </button>
        )
      })}
    </div>
  )
}
