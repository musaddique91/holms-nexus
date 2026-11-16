import { Info, KeyRound, Layers, Plug, SlidersHorizontal, X } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { cn } from '@/lib/utils'
import { useUi, type SettingsTab } from '@/store/ui'
import { ServersPanel } from './ServersPanel'
import { WorkspacesPanel } from './WorkspacesPanel'
import { ProvidersPanel } from './ProvidersPanel'
import { PreferencesPanel } from './PreferencesPanel'
import { AboutPanel } from './AboutPanel'

const TABS: { id: SettingsTab; label: string; icon: React.ElementType; hint: string }[] = [
  { id: 'workspaces', label: 'Workspaces', icon: Layers, hint: 'Groups of MCP servers' },
  { id: 'servers', label: 'MCP Servers', icon: Plug, hint: 'Data sources & tools' },
  { id: 'models', label: 'Models', icon: KeyRound, hint: 'Providers & API keys' },
  { id: 'preferences', label: 'Preferences', icon: SlidersHorizontal, hint: 'Theme & behaviour' },
  { id: 'about', label: 'About', icon: Info, hint: 'How it works' },
]

export function SettingsDialog() {
  const { settingsOpen, closeSettings, settingsTab, openSettings } = useUi()
  return (
    <Modal open={settingsOpen} onClose={closeSettings} hideClose className="h-[92dvh] sm:h-[86vh] sm:max-w-6xl">
      <div className="flex h-full min-h-0 flex-col sm:flex-row">
        <aside className="shrink-0 border-b border-line bg-bg-2/60 p-3 sm:w-60 sm:border-r sm:border-b-0 sm:p-4">
          <div className="mb-4 hidden px-2 pt-1 sm:block">
            <div className="text-lg font-semibold tracking-tight">Settings</div>
            <div className="text-xs text-muted">Configure your workspace</div>
          </div>
          <nav className="flex gap-1 overflow-x-auto sm:flex-col">
            {TABS.map((t) => (
              <button
                key={t.id}
                onClick={() => openSettings(t.id)}
                className={cn(
                  'flex shrink-0 items-center gap-3 rounded-xl px-3 py-2 text-left transition sm:py-2.5',
                  settingsTab === t.id ? 'bg-elev text-fg shadow-soft' : 'text-fg-2 hover:bg-elev-2/70 hover:text-fg',
                )}
              >
                <t.icon className={cn('size-4', settingsTab === t.id && 'text-accent')} />
                <span>
                  <span className="block text-sm font-medium">{t.label}</span>
                  <span className="hidden text-xs text-muted sm:block">{t.hint}</span>
                </span>
              </button>
            ))}
          </nav>
        </aside>
        <section className="relative flex min-h-0 min-w-0 flex-1 flex-col">
          <button
            onClick={closeSettings}
            className="ring-focus absolute top-4 right-4 z-10 rounded-lg p-1.5 text-muted transition hover:bg-elev-2 hover:text-fg"
            aria-label="Close settings"
          >
            <X className="size-4" />
          </button>
          <div className="min-h-0 flex-1 overflow-y-auto">
            {settingsTab === 'workspaces' && <WorkspacesPanel />}
            {settingsTab === 'servers' && <ServersPanel />}
            {settingsTab === 'models' && <ProvidersPanel />}
            {settingsTab === 'preferences' && <PreferencesPanel />}
            {settingsTab === 'about' && <AboutPanel />}
          </div>
        </section>
      </div>
    </Modal>
  )
}

export function PanelHeader({ title, description, actions }: { title: string; description: string; actions?: React.ReactNode }) {
  return (
    <div className="sticky top-0 z-[5] flex flex-wrap items-end justify-between gap-3 border-b border-line bg-elev/90 px-6 pt-5 pb-4 pr-14 backdrop-blur">
      <div>
        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
        <p className="mt-0.5 text-sm text-muted">{description}</p>
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}
