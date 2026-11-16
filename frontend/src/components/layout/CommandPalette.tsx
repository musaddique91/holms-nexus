import { Command } from 'cmdk'
import { useNavigate } from 'react-router-dom'
import { KeyRound, MessageSquare, Moon, Plug, Plus, Settings, SquarePen, Sun } from 'lucide-react'
import { useConversations, useServers, useWorkspaces } from '@/lib/queries'
import { useUi } from '@/store/ui'
import { Modal } from '@/components/ui/Modal'
import { ServerIcon } from '@/components/ui/ServerIcon'

export function CommandPalette() {
  const { paletteOpen, setPaletteOpen, openSettings, setAddServerOpen, setTheme, setExplorerServerId, setWorkspaceId } = useUi()
  const { data: workspaces = [] } = useWorkspaces()
  const { data: conversations = [] } = useConversations()
  const { data: servers = [] } = useServers()
  const navigate = useNavigate()
  const run = (fn: () => void) => { setPaletteOpen(false); fn() }

  return (
    <Modal open={paletteOpen} onClose={() => setPaletteOpen(false)} hideClose className="sm:max-w-xl sm:self-start sm:mt-[12vh]">
      <Command label="Command palette" className="flex flex-col">
        <Command.Input
          autoFocus
          placeholder="Search chats, servers and actions…"
          className="h-14 border-b border-line bg-transparent px-5 text-[15px] outline-none placeholder:text-muted"
        />
        <Command.List className="max-h-[52vh] overflow-y-auto p-2 [&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:pt-2 [&_[cmdk-group-heading]]:pb-1 [&_[cmdk-group-heading]]:text-[11px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:tracking-wider [&_[cmdk-group-heading]]:text-muted [&_[cmdk-group-heading]]:uppercase">
          <Command.Empty className="px-3 py-8 text-center text-sm text-muted">No results.</Command.Empty>
          <Command.Group heading="Actions">
            <Item icon={<SquarePen className="size-4" />} onSelect={() => run(() => navigate('/mcp'))}>New chat</Item>
            <Item icon={<Plus className="size-4" />} onSelect={() => run(() => { openSettings('servers'); setAddServerOpen(true) })}>Add MCP server</Item>
            <Item icon={<Plug className="size-4" />} onSelect={() => run(() => openSettings('servers'))}>Manage MCP servers</Item>
            <Item icon={<KeyRound className="size-4" />} onSelect={() => run(() => openSettings('models'))}>Models & API keys</Item>
            <Item icon={<Settings className="size-4" />} onSelect={() => run(() => openSettings('preferences'))}>Preferences</Item>
            <Item icon={<Sun className="size-4" />} onSelect={() => run(() => setTheme('light'))}>Light theme</Item>
            <Item icon={<Moon className="size-4" />} onSelect={() => run(() => setTheme('dark'))}>Dark theme</Item>
          </Command.Group>
          {workspaces.length > 1 && (
            <Command.Group heading="Switch workspace">
              {workspaces.map((w) => (
                <Item key={w.id} value={`workspace ${w.name}`} icon={<ServerIcon name={w.name} icon={w.icon ?? 'layers'} color={w.color} size="xs" />}
                  onSelect={() => run(() => { setWorkspaceId(w.id); navigate('/mcp') })}>
                  {w.name}
                </Item>
              ))}
            </Command.Group>
          )}
          {servers.length > 0 && (
            <Command.Group heading="Explore servers">
              {servers.map((s) => (
                <Item key={s.id} value={`server ${s.name}`} icon={<ServerIcon name={s.name} icon={s.icon} color={s.color} size="xs" />}
                  onSelect={() => run(() => { openSettings('servers'); setExplorerServerId(s.id) })}>
                  {s.name} <span className="ml-auto text-xs text-muted">{s.toolCount} tools</span>
                </Item>
              ))}
            </Command.Group>
          )}
          {conversations.length > 0 && (
            <Command.Group heading="Chats">
              {conversations.slice(0, 30).map((c) => (
                <Item key={c.id} value={`chat ${c.title} ${c.id}`} icon={<MessageSquare className="size-4" />} onSelect={() => run(() => navigate(`/mcp/c/${c.id}`))}>
                  <span className="truncate">{c.title}</span>
                </Item>
              ))}
            </Command.Group>
          )}
        </Command.List>
      </Command>
    </Modal>
  )
}

function Item({ children, icon, onSelect, value }: { children: React.ReactNode; icon: React.ReactNode; onSelect: () => void; value?: string }) {
  return (
    <Command.Item
      value={value}
      onSelect={onSelect}
      className="flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-fg-2 data-[selected=true]:bg-elev-2 data-[selected=true]:text-fg"
    >
      <span className="text-muted">{icon}</span>
      {children}
    </Command.Item>
  )
}
