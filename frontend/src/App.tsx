import type { ReactNode } from 'react'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { ChatView } from '@/components/chat/ChatView'
import { HolmesFrame } from '@/holmes/HolmesFrame'
import { AskPage } from '@/holmes/pages/AskPage'
import { DashboardPage } from '@/holmes/pages/DashboardPage'
import { IncidentDetailPage } from '@/holmes/pages/IncidentDetailPage'
import { IncidentsPage } from '@/holmes/pages/IncidentsPage'
import { IntegrationsPage } from '@/holmes/pages/IntegrationsPage'
import { ServicesPage } from '@/holmes/pages/ServicesPage'
import { SettingsPage } from '@/holmes/pages/SettingsPage'
import { auth } from '@/lib/auth'
import { LauncherPage } from '@/pages/LauncherPage'
import { LoginPage } from '@/pages/LoginPage'

function RequireAuth({ children }: { children: ReactNode }) {
  if (auth.token()) return <>{children}</>
  const next = encodeURIComponent(window.location.pathname + window.location.search)
  return <Navigate to={`/login?next=${next}`} replace />
}

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/', element: <RequireAuth><LauncherPage /></RequireAuth> },
  {
    path: '/mcp',
    element: <RequireAuth><AppShell app="mcp" /></RequireAuth>,
    children: [
      { index: true, element: <ChatView key="new" /> },
      { path: 'c/:id', element: <ChatView key="conv" /> },
      { path: '*', element: <ChatView key="new" /> },
    ],
  },
  {
    path: '/holmes',
    element: <RequireAuth><AppShell app="holmes" /></RequireAuth>,
    children: [
      {
        element: <HolmesFrame />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'incidents', element: <IncidentsPage /> },
          { path: 'incidents/:id', element: <IncidentDetailPage /> },
          { path: 'ask', element: <AskPage /> },
          { path: 'services', element: <ServicesPage /> },
          { path: 'integrations', element: <IntegrationsPage /> },
          { path: 'settings', element: <SettingsPage /> },
          { path: '*', element: <Navigate to="/holmes" replace /> },
        ],
      },
    ],
  },
  // Old MCP links (/c/:id) and anything unknown
  { path: '/c/:id', element: <Navigate to="/mcp" replace /> },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
