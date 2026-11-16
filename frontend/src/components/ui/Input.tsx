import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

const base =
  'w-full rounded-xl border border-line bg-elev px-3 text-sm text-fg placeholder:text-muted/70 transition-colors outline-none hover:border-line-strong focus:border-accent/60 focus:ring-4 focus:ring-accent/10 disabled:opacity-60'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...rest },
  ref,
) {
  return <input ref={ref} className={cn(base, 'h-9', className)} {...rest} />
})

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, ...rest },
  ref,
) {
  return <textarea ref={ref} className={cn(base, 'py-2.5 leading-relaxed', className)} {...rest} />
})

export function Field({ label, hint, children, className, required }: {
  label: ReactNode
  hint?: ReactNode
  children: ReactNode
  className?: string
  required?: boolean
}) {
  return (
    <label className={cn('block space-y-1.5', className)}>
      <span className="flex items-center gap-1 text-[13px] font-medium text-fg-2">
        {label}
        {required && <span className="text-accent">*</span>}
      </span>
      {children}
      {hint && <span className="block text-xs text-muted">{hint}</span>}
    </label>
  )
}
