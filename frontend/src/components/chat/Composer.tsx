import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'
import { ArrowUp, Square } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { McpServer, Provider } from '@/lib/types'
import { ServerPicker } from './ServerPicker'
import { ModelPicker } from './ModelPicker'

export interface ComposerHandle {
  focus: () => void
  setText: (t: string) => void
}

export const Composer = forwardRef<ComposerHandle, {
  onSend: (text: string) => void
  onStop: () => void
  busy: boolean
  servers: McpServer[]
  serverIds: string[]
  onServerIdsChange: (ids: string[]) => void
  providers: Provider[]
  providerId: string | null
  model: string | null
  onModelChange: (providerId: string, model: string) => void
  autoFocus?: boolean
  placeholder?: string
}>(function Composer(props, ref) {
  const [text, setText] = useState('')
  const ta = useRef<HTMLTextAreaElement>(null)

  useImperativeHandle(ref, () => ({
    focus: () => ta.current?.focus(),
    setText: (t) => {
      setText(t)
      requestAnimationFrame(() => {
        ta.current?.focus()
        ta.current?.setSelectionRange(t.length, t.length)
      })
    },
  }))

  const fit = () => {
    const el = ta.current
    if (!el) return
    el.style.height = '0px'
    el.style.height = `${Math.min(el.scrollHeight, 240)}px`
  }

  useEffect(fit, [text])

  // Re-measure when the width changes (sidebar toggles, window resizes, initial layout).
  useEffect(() => {
    const el = ta.current
    if (!el) return
    let width = el.clientWidth
    const ro = new ResizeObserver(() => {
      if (el.clientWidth !== width) {
        width = el.clientWidth
        fit()
      }
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  useEffect(() => {
    if (props.autoFocus) ta.current?.focus()
  }, [props.autoFocus])

  const submit = () => {
    const t = text.trim()
    if (!t || props.busy) return
    props.onSend(t)
    setText('')
  }

  const canSend = text.trim().length > 0 && !props.busy

  return (
    <div
      className={cn(
        'glass rounded-[22px] border border-line-strong shadow-soft transition-all',
        'focus-within:border-accent/40 focus-within:shadow-[0_0_0_4px_var(--accent-soft),var(--shadow)]',
      )}
    >
      <textarea
        ref={ta}
        value={text}
        rows={1}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault()
            submit()
          }
        }}
        placeholder={props.placeholder ?? 'Ask anything about your data…'}
        className="block max-h-60 min-h-[52px] w-full resize-none bg-transparent px-4 pt-3.5 pb-1 text-[15px] leading-relaxed text-fg outline-none placeholder:text-muted/70"
      />
      <div className="flex items-center gap-2 px-2.5 pt-1 pb-2.5">
        <ServerPicker servers={props.servers} selected={props.serverIds} onChange={props.onServerIdsChange} />
        <div className="ml-auto flex items-center gap-1.5">
          <ModelPicker providers={props.providers} providerId={props.providerId} model={props.model} onChange={props.onModelChange} />
          {props.busy ? (
            <button
              type="button"
              onClick={props.onStop}
              className="ring-focus flex size-9 items-center justify-center rounded-full bg-fg text-bg transition hover:opacity-85"
              aria-label="Stop generating"
            >
              <Square className="size-3.5 fill-current" />
            </button>
          ) : (
            <button
              type="button"
              onClick={submit}
              disabled={!canSend}
              className={cn(
                'ring-focus flex size-9 items-center justify-center rounded-full transition-all',
                canSend ? 'bg-gradient-accent text-white shadow-[0_6px_18px_-4px_var(--accent)] hover:scale-105' : 'bg-elev-2 text-muted',
              )}
              aria-label="Send message"
            >
              <ArrowUp className="size-[18px]" strokeWidth={2.5} />
            </button>
          )}
        </div>
      </div>
    </div>
  )
})
