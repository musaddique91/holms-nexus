import { useMemo, useState } from 'react'
import { NavLink, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { Moon, MoreHorizontal, PanelLeftClose, Pencil, Pin, PinOff, Plug, Search, Settings, SquarePen, Sun, Trash2 } from 'lucide-react'
import { api } from '@/lib/api'
import { useConversations, useServers, useWorkspaces } from '@/lib/queries'
import type { ConversationSummary } from '@/lib/types'
import { cn, dateGroup, modKey } from '@/lib/utils'
import { useUi, type Theme } from '@/store/ui'
import { useChat } from '@/store/chat'
import { Wordmark } from './Logo'
import { AppSwitcher } from './AppSwitcher'
import { SignOutButton } from './HolmesSidebar'
import { Kbd, StatusDot } from '@/components/ui/Badge'
import { Popover } from '@/components/ui/Popover'
import { confirm } from '@/components/ui/Confirm'
import { Spinner } from '@/components/ui/Spinner'

const GROUP_ORDER = ['Pinned', 'Today', 'Yesterday', 'Previous 7 days', 'Previous 30 days', 'Older']

export function Sidebar() {
  const [q, setQ] = useState('')
  const workspaceId = useUi((s) => s.workspaceId)
  const { data: workspaces = [] } = useWorkspaces()
  const activeWorkspace = workspaces.find((w) => w.id === workspaceId) ?? workspaces.find((w) => w.isDefault)
  const { data: conversations = [], isFetching } = useConversations(q, activeWorkspace?.id)
  const { data: servers = [] } = useServers()
  const { setSidebarOpen, openSettings, theme, setTheme, setPaletteOpen } = useUi()
  const navigate = useNavigate()

  const groups = useMemo(() => {
    const map = new Map<string, ConversationSummary[]>()
    for (const c of conversations) {
      const g = c.pinned ? 'Pinned' : dateGroup(c.updatedAt)
      map.set(g, [...(map.get(g) ?? []), c])
    }
    return GROUP_ORDER.filter((g) => map.has(g)).map((g) => [g, map.get(g)!] as const)
  }, [conversations])

  const enabled = servers.filter((s) => s.enabled)
  const connected = enabled.filter((s) => s.status === 'CONNECTED')
  const tools = connected.reduce((n, s) => n + s.toolCount, 0)
  const hasError = enabled.some((s) => s.status === 'ERROR')
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

      <div className="space-y-2 px-3 pb-2">
        <AppSwitcher current="mcp" />
        <div className="h-px bg-line" />
        <button
          onClick={() => { navigate('/mcp'); useChat.getState().setDraft({}); closeOnMobile() }}
          className="ring-focus group flex h-10 w-full items-center gap-2.5 rounded-xl border border-line bg-elev px-3 text-sm font-medium shadow-soft transition hover:border-accent/40"
        >
          <span className="flex size-6 items-center justify-center rounded-lg bg-gradient-accent text-white">
            <SquarePen className="size-3.5" />
          </span>
          New chat
          <span className="ml-auto flex gap-0.5 opacity-60 group-hover:opacity-100"><Kbd>{modKey}</Kbd><Kbd>⇧</Kbd><Kbd>O</Kbd></span>
        </button>
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search chats"
            className="h-9 w-full rounded-xl border border-transparent bg-elev-2/70 pr-14 pl-9 text-sm outline-none placeholder:text-muted/80 focus:border-line-strong"
          />
          <span className="absolute top-1/2 right-2 -translate-y-1/2">
            {isFetching && q ? <Spinner className="size-3.5 text-muted" /> : <button onClick={() => setPaletteOpen(true)} className="flex gap-0.5"><Kbd>{modKey}</Kbd><Kbd>K</Kbd></button>}
          </span>
        </div>
      </div>

      <nav className="flex-1 space-y-4 overflow-y-auto px-2 py-2">
        {groups.length === 0 && (
          <div className="px-3 py-8 text-center text-sm text-muted">
            {q ? 'No chats match your search.' : `No chats in ${activeWorkspace?.name ?? 'this workspace'} yet.`}
          </div>
        )}
        {groups.map(([group, items]) => (
          <div key={group}>
            <div className="px-3 pb-1 text-[11px] font-semibold tracking-wider text-muted/80 uppercase">{group}</div>
            <AnimatePresence initial={false}>
              {items.map((c) => <ConversationItem key={c.id} c={c} onNavigate={closeOnMobile} />)}
            </AnimatePresence>
          </div>
        ))}
      </nav>

      <div className="space-y-1 border-t border-line p-2">
        <button
          onClick={() => openSettings('servers')}
          className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition hover:bg-elev-2"
        >
          <span className="relative flex size-8 items-center justify-center rounded-lg border border-line bg-elev">
            <Plug className="size-4 text-fg-2" />
            <span className="absolute -top-0.5 -right-0.5">
              <StatusDot status={hasError ? 'ERROR' : connected.length ? 'CONNECTED' : 'DISCONNECTED'} />
            </span>
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-medium">MCP servers</span>
            <span className="block truncate text-xs text-muted">
              {enabled.length ? `${connected.length}/${enabled.length} connected · ${tools} tools` : 'None connected yet'}
            </span>
          </span>
        </button>
        <div className="flex items-center gap-1 px-1">
          <button
            onClick={() => openSettings('preferences')}
            className="flex flex-1 items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-fg-2 transition hover:bg-elev-2 hover:text-fg"
          >
            <Settings className="size-4" /> Settings
          </button>
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
    </div>
  )
}

function ConversationItem({ c, onNavigate }: { c: ConversationSummary; onNavigate: () => void }) {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const streaming = useChat((s) => !!s.streams[c.id])
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(c.title)
  const [menu, setMenu] = useState(false)

  const save = async (patch: Partial<ConversationSummary>) => {
    await api.conversations.update(c.id, patch)
    qc.invalidateQueries({ queryKey: ['conversations'] })
    qc.invalidateQueries({ queryKey: ['conversation', c.id] })
  }

  const remove = async () => {
    setMenu(false)
    if (!(await confirm({ title: 'Delete chat?', message: `“${c.title}” and its messages will be permanently deleted.`, confirmLabel: 'Delete', danger: true }))) return
    await api.conversations.remove(c.id)
    qc.invalidateQueries({ queryKey: ['conversations'] })
    if (id === c.id) navigate('/mcp')
  }

  return (
    <motion.div layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, height: 0 }} className="group relative">
      {editing ? (
        <input
          autoFocus
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={() => { setEditing(false); if (title.trim() && title !== c.title) save({ title: title.trim() }) }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') (e.target as HTMLInputElement).blur()
            if (e.key === 'Escape') { setTitle(c.title); setEditing(false) }
          }}
          className="h-9 w-full rounded-lg border border-accent/50 bg-elev px-3 text-sm outline-none"
        />
      ) : (
        <NavLink
          to={`/mcp/c/${c.id}`}
          onClick={onNavigate}
          onDoubleClick={() => setEditing(true)}
          className={({ isActive }) =>
            cn(
              'flex h-9 items-center gap-2 rounded-lg px-3 pr-8 text-sm transition',
              isActive ? 'bg-elev text-fg shadow-soft' : 'text-fg-2 hover:bg-elev-2/70 hover:text-fg',
            )
          }
        >
          {streaming && <Spinner className="size-3 shrink-0 text-accent" />}
          <span className="truncate">{c.title}</span>
        </NavLink>
      )}
      {!editing && (
        <div className={cn('absolute top-1/2 right-1 -translate-y-1/2', menu ? 'opacity-100' : 'opacity-0 group-hover:opacity-100')}>
          <Popover
            side="bottom"
            align="end"
            className="w-44"
            open={menu}
            onOpenChange={setMenu}
            trigger={({ toggle }) => (
              <button onClick={toggle} className="rounded-md p-1 text-muted hover:bg-elev-2 hover:text-fg" aria-label="Chat options">
                <MoreHorizontal className="size-4" />
              </button>
            )}
          >
            <MenuItem icon={c.pinned ? PinOff : Pin} label={c.pinned ? 'Unpin' : 'Pin'} onClick={() => { setMenu(false); save({ pinned: !c.pinned }) }} />
            <MenuItem icon={Pencil} label="Rename" onClick={() => { setMenu(false); setEditing(true) }} />
            <MenuItem icon={Trash2} label="Delete" danger onClick={remove} />
          </Popover>
        </div>
      )}
    </motion.div>
  )
}

export function MenuItem({ icon: Icon, label, onClick, danger }: { icon: React.ElementType; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-sm transition',
        danger ? 'text-err hover:bg-err/10' : 'text-fg-2 hover:bg-elev-2 hover:text-fg',
      )}
    >
      <Icon className="size-4" /> {label}
    </button>
  )
}
