import { KeyRound } from 'lucide-react'
import type { ReactNode } from 'react'

/** Write-only secret: shows a masked hint when saved; Replace / Keep saved; null = keep. */
export function SecretField(props: {
  id: string
  label: ReactNode
  placeholder: string
  isSet: boolean
  hint: string | null
  value: string | null
  onChange: (v: string | null) => void
  help?: ReactNode
  className?: string
}) {
  const removing = props.isSet && props.value === ''
  return (
    <div className={props.className}>
      <label className="label" htmlFor={props.id}>{props.label}</label>
      {props.isSet && props.value === null ? (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-subtle px-3 py-2">
          <KeyRound className="size-4 text-fg-3" />
          <span className="font-mono text-sm text-fg">{props.hint ?? '••••••••'}</span>
          <span className="chip">saved, encrypted</span>
          <button type="button" className="btn-ghost ml-auto h-8" onClick={() => props.onChange('')}>Replace</button>
        </div>
      ) : (
        <div className="flex gap-2">
          <input
            id={props.id}
            type="password"
            autoComplete="new-password"
            className="input font-mono"
            value={props.value ?? ''}
            onChange={(e) => props.onChange(e.target.value)}
            placeholder={props.placeholder}
          />
          {props.isSet && (
            <button type="button" className="btn-ghost h-10 shrink-0" onClick={() => props.onChange(null)}>
              Keep saved
            </button>
          )}
        </div>
      )}
      <p className="mt-1.5 text-xs text-fg-3">
        {removing ? <span className="text-amber-700 dark:text-amber-300">Saving while this is empty removes the saved value.</span> : props.help}
      </p>
    </div>
  )
}
