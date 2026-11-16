import { useQuery } from '@tanstack/react-query'
import { Activity, Boxes, LayoutDashboard, LogOut, MessageSquareText, Moon, PanelLeftClose, Plug, Settings2, Sun } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { api } from '@/holmes/api/client'
import { auth } from '@/lib/auth'
import { cn } from '@/lib/utils'
import { useUi, type Theme } from '@/store/ui'
import { AppSwitcher } from './AppSwitcher'
import { Wordmark } from './Logo'

const NAV = [
  { to: '/holmes', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/holmes/incidents', label: 'Incidents', icon: Activity, badge: true },
  { to: '/holmes/ask', label: 'Ask Intelligence', icon: MessageSquareText },
  { to: '/holmes/services', label: 'Services', icon: Boxes },
  { to: '/holmes/integrations', label: 'Integrations', icon: Plug },
  { to: '/holmes/settings', label: 'Settings', icon: Settings2 },
]

const CLOSED = new Set(['RESOLVED', 'IGNORED'])

/** Intelligence navigation in MCP's sidebar style. */
export function HolmesSidebar() {
  const { setSidebarOpen, theme, setTheme } = useUi()
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard, refetchInterval: 30_000 })
  const me = useQuery({ queryKey: ['me'], queryFn: api.me, staleTime: Infinity })
  const open = Object.entries(dashboard.data?.byStatus ?? {})
    .filter(([status]) => !CLOSED.has(status))
    .reduce((n, [, count]) => n + count, 0)
  const isDark = document.documentElement.classList.contains('dark')
  const closeOnMobile = () => window.innerWidth < 1024 && setSidebarOpen(false)

  return (
    <div className="flex h-full w-[280px] flex-col border-r border-line bg-bg-2/80 backdrop-blur-xl">
      <div className="flex h-14 items-center justify-between px-4">
        <Wordmark />
        <button
          onClick={() => setSidebarOpen(false)}
          className="ring-focus rounded-lg p-1.5 text-muted transition hover:bg-elev-2 hover:text-fg"
          aria-label="Close sidebar"
        >
          <PanelLeftClose className="size-4" />
        </button>
      </div>
      <div className="px-3 pb-3">
        <AppSwitcher current="holmes" />
        <div className="mt-3 h-px bg-line" />
      </div>

      <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 py-1">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={closeOnMobile}
            className={({ isActive }) =>
              cn(
                'ring-focus flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition',
                isActive ? 'bg-elev font-medium text-fg shadow-soft' : 'text-fg-2 hover:bg-elev-2 hover:text-fg',
              )
            }
          >
            {({ isActive }) => (
              <>
                <item.icon className={cn('size-4', isActive && 'text-accent')} />
                <span className="flex-1">{item.label}</span>
                {item.badge && open > 0 && (
                  <span className="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-semibold text-accent tabular-nums">
                    {open}
                  </span>
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="flex items-center gap-1 border-t border-line p-2">
        <span className="flex min-w-0 flex-1 items-center gap-2 px-2 text-sm text-fg-2">
          <span className="flex size-7 shrink-0 items-center justify-center rounded-lg border border-line bg-elev text-xs font-semibold uppercase">
            {(me.data?.username ?? '?').slice(0, 1)}
          </span>
          <span className="truncate">{me.data?.username ?? ''}</span>
        </span>
        <button
          onClick={() => setTheme(isDark ? 'light' : ((localStorage.getItem('nexus-last-dark-theme') as Theme) || 'dark'))}
          className="rounded-lg p-2 text-fg-2 transition hover:bg-elev-2 hover:text-fg"
          aria-label="Toggle theme"
          title={`Theme: ${theme}`}
        >
          {isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
        </button>
        <SignOutButton />
      </div>
    </div>
  )
}

export function SignOutButton() {
  return (
    <button
      onClick={() => {
        auth.clear()
        window.location.assign('/login')
      }}
      className="rounded-lg p-2 text-fg-2 transition hover:bg-elev-2 hover:text-fg"
      aria-label="Sign out"
      title="Sign out"
    >
      <LogOut className="size-4" />
    </button>
  )
}
