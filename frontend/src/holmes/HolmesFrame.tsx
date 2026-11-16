import { PanelLeft } from 'lucide-react'
import { Outlet } from 'react-router-dom'
import { useUi } from '@/store/ui'
import { ThemePicker } from '@/components/layout/ThemePicker'

/** Scrolling page area for the Intelligence screens, inside MCP's shell. */
export function HolmesFrame() {
  const { sidebarOpen, setSidebarOpen } = useUi()
  return (
    <div className="relative flex min-w-0 flex-1 flex-col">
      {/* Top bar: sidebar toggle (left) + theme picker (right) */}
      <div className="pointer-events-none absolute inset-x-0 top-0 z-20 flex h-14 items-center justify-between px-3 [&>*]:pointer-events-auto">
        {!sidebarOpen ? (
          <button
            onClick={() => setSidebarOpen(true)}
            className="ring-focus glass rounded-xl border border-line p-2 text-fg-2 transition hover:text-fg"
            aria-label="Open sidebar"
          >
            <PanelLeft className="size-4" />
          </button>
        ) : (
          <span />
        )}
        <ThemePicker />
      </div>
      <div className="flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-6xl px-4 pt-14 pb-10 md:px-8 lg:pt-8">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
