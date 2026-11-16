import { useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { useUi } from '@/store/ui'
import type { AppId } from '@/apps'
import { HolmesSidebar } from './HolmesSidebar'
import { Sidebar } from './Sidebar'
import { CommandPalette } from './CommandPalette'
import { SettingsDialog } from '@/components/settings/SettingsDialog'
import { ConfirmHost } from '@/components/ui/Confirm'

export function AppShell({ app }: { app: AppId }) {
  const AppSidebar = app === 'holmes' ? HolmesSidebar : Sidebar
  const { sidebarOpen, setSidebarOpen, setPaletteOpen, paletteOpen, openSettings } = useUi()
  const navigate = useNavigate()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey
      if (mod && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen(!paletteOpen)
      } else if (app === 'mcp' && mod && e.shiftKey && e.key.toLowerCase() === 'o') {
        e.preventDefault()
        navigate('/mcp')
      } else if (mod && e.key === ',') {
        e.preventDefault()
        openSettings()
      } else if (mod && e.key.toLowerCase() === 'b') {
        e.preventDefault()
        setSidebarOpen(!sidebarOpen)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [app, paletteOpen, sidebarOpen, navigate, setPaletteOpen, setSidebarOpen, openSettings])

  return (
    <div className="relative flex h-dvh overflow-hidden">
      <Aurora />
      {/* Desktop sidebar */}
      <AnimatePresence initial={false}>
        {sidebarOpen && (
          <motion.aside
            key="sidebar"
            initial={{ width: 0, opacity: 0 }}
            animate={{ width: 280, opacity: 1 }}
            exit={{ width: 0, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 400, damping: 40 }}
            className="relative z-20 hidden shrink-0 overflow-hidden lg:block"
          >
            <AppSidebar />
          </motion.aside>
        )}
      </AnimatePresence>
      {/* Mobile drawer */}
      <AnimatePresence>
        {sidebarOpen && (
          <div className="fixed inset-0 z-40 lg:hidden">
            <motion.div className="absolute inset-0 bg-black/40 backdrop-blur-sm" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setSidebarOpen(false)} />
            <motion.aside
              className="absolute inset-y-0 left-0 shadow-2xl"
              initial={{ x: -300 }}
              animate={{ x: 0 }}
              exit={{ x: -300 }}
              transition={{ type: 'spring', stiffness: 400, damping: 40 }}
            >
              <AppSidebar />
            </motion.aside>
          </div>
        )}
      </AnimatePresence>
      <main className="relative z-10 flex min-w-0 flex-1">
        <Outlet />
      </main>
      <SettingsDialog />
      <CommandPalette />
      <ConfirmHost />
    </div>
  )
}

export function Aurora() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden>
      <div className="absolute -top-[30%] left-[20%] h-[70vh] w-[70vh] animate-float rounded-full blur-[120px]" style={{ background: 'var(--aurora-a)' }} />
      <div className="absolute top-[10%] -right-[10%] h-[60vh] w-[60vh] animate-float rounded-full blur-[120px] [animation-delay:-6s]" style={{ background: 'var(--aurora-b)' }} />
      <div className="absolute -bottom-[25%] left-[35%] h-[55vh] w-[55vh] animate-float rounded-full blur-[120px] [animation-delay:-12s]" style={{ background: 'var(--aurora-c)' }} />
      {/* Light theme signature: thin red rule with a halftone fade */}
      <div className="absolute inset-x-0 top-0 h-[3px] bg-accent dark:hidden" />
      <div className="halftone absolute inset-x-0 top-[3px] h-5 opacity-40 [mask-image:linear-gradient(to_bottom,black,transparent)] dark:hidden" />
      <div
        className="absolute inset-0 opacity-[0.45] dark:opacity-[0.25]"
        style={{
          backgroundImage: 'radial-gradient(var(--dots) 1px, transparent 1px)',
          backgroundSize: '22px 22px',
          maskImage: 'radial-gradient(ellipse at center, black 20%, transparent 75%)',
        }}
      />
    </div>
  )
}
