import { ArrowRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { APPS, lastApp, rememberApp } from '@/apps'
import { Aurora } from '@/components/layout/AppShell'
import { SignOutButton } from '@/components/layout/HolmesSidebar'
import { Wordmark } from '@/components/layout/Logo'
import { cn } from '@/lib/utils'

/** After sign-in: pick an application. The last one used is highlighted. */
export function LauncherPage() {
  const navigate = useNavigate()
  const last = lastApp()
  return (
    <div className="relative flex min-h-dvh flex-col">
      <Aurora />
      <header className="relative z-10 flex h-16 items-center justify-between px-5">
        <Wordmark />
        <SignOutButton />
      </header>
      <main className="relative z-10 flex flex-1 items-center justify-center p-4">
        <div className="w-full max-w-3xl">
          <h1 className="text-center text-2xl font-semibold tracking-tight md:text-3xl">Choose an application</h1>
          <p className="mt-2 text-center text-sm text-muted">You can switch at any time from the sidebar.</p>
          <div className="mt-8 grid gap-4 md:grid-cols-2">
            {APPS.map((a) => (
              <button
                key={a.id}
                onClick={() => {
                  rememberApp(a.id)
                  navigate(a.path)
                }}
                className={cn(
                  'ring-focus group flex flex-col rounded-2xl border bg-panel p-6 text-left shadow-soft backdrop-blur-xl transition hover:-translate-y-0.5 hover:border-accent/50',
                  a.id === last ? 'border-accent/40' : 'border-line',
                )}
              >
                <span className="flex size-11 items-center justify-center rounded-xl bg-accent-soft text-accent">
                  <a.icon className="size-5" />
                </span>
                <span className="mt-5 flex items-center gap-2 text-lg font-semibold">
                  {a.name}
                  {a.id === last && (
                    <span className="rounded-full bg-elev-2 px-2 py-0.5 text-[11px] font-medium text-muted">Last used</span>
                  )}
                </span>
                <span className="text-sm font-medium text-accent">{a.tagline}</span>
                <span className="mt-2 text-sm leading-relaxed text-muted">{a.description}</span>
                <span className="mt-5 flex items-center gap-1.5 text-sm font-medium text-fg-2 group-hover:text-fg">
                  Open {a.name} <ArrowRight className="size-4 transition group-hover:translate-x-0.5" />
                </span>
              </button>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}
