import { create } from 'zustand'

export type Theme = 'dark' | 'light' | 'system' | 'emerald' | 'ember' | 'tide' | 'emerald-light' | 'ember-light' | 'tide-light'
export type SettingsTab = 'workspaces' | 'servers' | 'models' | 'preferences' | 'about'

const THEME_BG: Record<Theme, string> = {
  dark: '#090b10',
  light: '#f4f3f0',
  system: '#090b10',
  emerald: '#0b1410',
  ember: '#161618',
  tide: '#11171b',
  'emerald-light': '#f0f7f2',
  'ember-light': '#f7f4f2',
  'tide-light': '#f0f5f8',
}

function applyTheme(theme: Theme) {
  const cl = document.documentElement.classList
  cl.remove('dark', 'theme-emerald', 'theme-ember', 'theme-tide', 'theme-emerald-light', 'theme-ember-light', 'theme-tide-light')
  let bg = THEME_BG[theme]
  if (theme === 'light') {
    bg = THEME_BG.light
  } else if (theme === 'dark') {
    cl.add('dark')
  } else if (theme === 'system') {
    const dark = matchMedia('(prefers-color-scheme: dark)').matches
    if (dark) cl.add('dark')
    bg = dark ? THEME_BG.dark : THEME_BG.light
  } else if (theme === 'emerald') {
    cl.add('dark', 'theme-emerald')
  } else if (theme === 'ember') {
    cl.add('dark', 'theme-ember')
  } else if (theme === 'tide') {
    cl.add('dark', 'theme-tide')
  } else if (theme === 'emerald-light') {
    cl.add('theme-emerald-light')
  } else if (theme === 'ember-light') {
    cl.add('theme-ember-light')
  } else if (theme === 'tide-light') {
    cl.add('theme-tide-light')
  }
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', bg)
}

function load<T>(key: string, fallback: T): T {
  try {
    const v = localStorage.getItem(key)
    return v == null ? fallback : (JSON.parse(v) as T)
  } catch {
    return fallback
  }
}
function save(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)) } catch { /* ignore */ }
}

const DARK_FAMILY = new Set<Theme>(['dark', 'emerald', 'ember', 'tide'])

interface UiState {
  theme: Theme
  setTheme: (t: Theme) => void
  lastDarkTheme: Theme
  sidebarOpen: boolean
  setSidebarOpen: (v: boolean) => void
  settingsOpen: boolean
  settingsTab: SettingsTab
  openSettings: (tab?: SettingsTab) => void
  closeSettings: () => void
  addServerOpen: boolean
  setAddServerOpen: (v: boolean) => void
  paletteOpen: boolean
  setPaletteOpen: (v: boolean) => void
  explorerServerId: string | null
  setExplorerServerId: (id: string | null) => void
  workspaceId: string | null
  setWorkspaceId: (id: string | null) => void
}

const initialTheme = (() => {
  try { return (localStorage.getItem('nexus-theme') as Theme) || 'dark' } catch { return 'dark' as Theme }
})()

const initialLastDark = (() => {
  try { return (localStorage.getItem('nexus-last-dark-theme') as Theme) || 'dark' } catch { return 'dark' as Theme }
})()

export const useUi = create<UiState>((set) => ({
  theme: initialTheme,
  lastDarkTheme: initialLastDark,
  setTheme: (theme) => {
    try { localStorage.setItem('nexus-theme', theme) } catch { /* ignore */ }
    // Remember last dark-family theme so the sun/moon toggle can restore it
    if (DARK_FAMILY.has(theme)) {
      try { localStorage.setItem('nexus-last-dark-theme', theme) } catch { /* ignore */ }
      set({ theme, lastDarkTheme: theme })
    } else {
      set({ theme })
    }
    applyTheme(theme)
  },
  sidebarOpen: load('nexus-sidebar', typeof window !== 'undefined' ? window.innerWidth >= 1024 : true),
  setSidebarOpen: (sidebarOpen) => { save('nexus-sidebar', sidebarOpen); set({ sidebarOpen }) },
  settingsOpen: false,
  settingsTab: 'servers',
  openSettings: (tab) => set((s) => ({ settingsOpen: true, settingsTab: tab ?? s.settingsTab })),
  closeSettings: () => set({ settingsOpen: false }),
  addServerOpen: false,
  setAddServerOpen: (addServerOpen) => set({ addServerOpen }),
  paletteOpen: false,
  setPaletteOpen: (paletteOpen) => set({ paletteOpen }),
  explorerServerId: null,
  setExplorerServerId: (explorerServerId) => set({ explorerServerId }),
  workspaceId: load<string | null>('nexus-workspace', null),
  setWorkspaceId: (workspaceId) => { save('nexus-workspace', workspaceId); set({ workspaceId }) },
}))

// Apply saved theme immediately on module load (avoids flash on first render)
if (typeof window !== 'undefined') {
  applyTheme(initialTheme)

  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (useUi.getState().theme === 'system') applyTheme('system')
  })
}
