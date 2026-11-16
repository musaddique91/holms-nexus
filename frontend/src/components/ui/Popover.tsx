import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { cn } from '@/lib/utils'

/** Lightweight anchored popover. `side` controls vertical placement relative to the trigger. */
export function Popover({ trigger, children, align = 'start', side = 'top', className, open: controlled, onOpenChange }: {
  trigger: (props: { open: boolean; toggle: () => void }) => ReactNode
  children: ReactNode | ((close: () => void) => ReactNode)
  align?: 'start' | 'end'
  side?: 'top' | 'bottom'
  className?: string
  open?: boolean
  onOpenChange?: (v: boolean) => void
}) {
  const [inner, setInner] = useState(false)
  const open = controlled ?? inner
  const setOpen = (v: boolean) => (onOpenChange ? onOpenChange(v) : setInner(v))
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  })

  const close = () => setOpen(false)
  return (
    <div ref={ref} className="relative">
      {trigger({ open, toggle: () => setOpen(!open) })}
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: side === 'top' ? 6 : -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: side === 'top' ? 6 : -6, scale: 0.98 }}
            transition={{ duration: 0.14 }}
            className={cn(
              'absolute z-40 min-w-56 rounded-2xl border border-line-strong bg-elev p-1.5 shadow-soft',
              side === 'top' ? 'bottom-full mb-2' : 'top-full mt-2',
              align === 'start' ? 'left-0' : 'right-0',
              className,
            )}
          >
            {typeof children === 'function' ? children(close) : children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
