import { useMutation } from '@tanstack/react-query'
import { Eye, EyeOff, Loader2, Lock, User } from 'lucide-react'
import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '@/holmes/api/client'
import { Aurora } from '@/components/layout/AppShell'
import { Logo } from '@/components/layout/Logo'
import { auth } from '@/lib/auth'

/** One sign-in for Intelligence and MCP. */
export function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [show, setShow] = useState(false)
  const next = params.get('next')
  const target = next && next.startsWith('/') && !next.startsWith('//') ? next : '/'

  const login = useMutation({
    mutationFn: () => api.login(username.trim(), password),
    onSuccess: (r) => {
      auth.set(r.token)
      navigate(target, { replace: true })
    },
  })

  if (auth.token()) return <Navigate to={target} replace />

  return (
    <div className="relative flex min-h-dvh items-center justify-center p-4">
      <Aurora />
      <form
        onSubmit={(e) => {
          e.preventDefault()
          login.mutate()
        }}
        className="relative z-10 w-full max-w-sm rounded-2xl border border-line bg-panel p-7 shadow-soft backdrop-blur-xl"
      >
        <div className="flex flex-col items-center text-center">
          <Logo size={44} />
          <h1 className="mt-4 text-xl font-semibold tracking-tight">Sign in to MVX Nexus</h1>
          <p className="mt-1 text-sm text-muted">Intelligence and MCP, one account</p>
        </div>

        <label className="mt-7 block text-xs font-medium tracking-wide text-muted uppercase" htmlFor="username">
          Username
        </label>
        <div className="relative mt-1.5">
          <User className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
          <input
            id="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="h-10 w-full rounded-xl border border-line bg-elev pr-3 pl-9 text-sm outline-none focus:border-accent focus:ring-4 focus:ring-accent/15"
            required
          />
        </div>

        <label className="mt-4 block text-xs font-medium tracking-wide text-muted uppercase" htmlFor="password">
          Password
        </label>
        <div className="relative mt-1.5">
          <Lock className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
          <input
            id="password"
            type={show ? 'text' : 'password'}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="h-10 w-full rounded-xl border border-line bg-elev pr-10 pl-9 text-sm outline-none focus:border-accent focus:ring-4 focus:ring-accent/15"
            required
            autoFocus
          />
          <button
            type="button"
            onClick={() => setShow((s) => !s)}
            className="absolute top-1/2 right-2 -translate-y-1/2 rounded-md p-1 text-muted hover:text-fg"
            aria-label={show ? 'Hide password' : 'Show password'}
          >
            {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
          </button>
        </div>

        {login.error && (
          <p role="alert" className="mt-4 rounded-xl border border-err/25 bg-err/10 px-3 py-2 text-sm text-err">
            Wrong username or password
          </p>
        )}

        <button type="submit" disabled={login.isPending || !password} className="btn-primary mt-6 h-10 w-full">
          {login.isPending && <Loader2 className="size-4 animate-spin" />} Sign in
        </button>
      </form>
    </div>
  )
}
