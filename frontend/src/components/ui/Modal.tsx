import type { ReactNode } from 'react'
import { useEscape } from '@/lib/useEscape'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'motion/react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Modal({ open, onClose, children, className, title, description, hideClose }: {
  open: boolean
  onClose: () => void
  children: ReactNode
  className?: string
  title?: ReactNode
  description?: ReactNode
  hideClose?: boolean
}) {
  useEscape(open, onClose)

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-6">
          <motion.div
            className="absolute inset-0 bg-black/50 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            initial={{ opacity: 0, y: 24, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.98 }}
            transition={{ type: 'spring', stiffness: 380, damping: 32 }}
            className={cn(
              'relative flex max-h-[92dvh] w-full flex-col overflow-hidden rounded-t-2xl border border-line-strong bg-elev shadow-2xl sm:max-w-lg sm:rounded-2xl',
              className,
            )}
          >
            {(title || !hideClose) && (
              <div className="flex shrink-0 items-start justify-between gap-4 px-6 pt-5 pb-3">
                <div className="min-w-0">
                  {title && <h2 className="text-[17px] font-semibold tracking-tight">{title}</h2>}
                  {description && <p className="mt-1 text-sm text-muted">{description}</p>}
                </div>
                {!hideClose && (
                  <button
                    onClick={onClose}
                    className="ring-focus -mr-2 rounded-lg p-1.5 text-muted transition hover:bg-elev-2 hover:text-fg"
                    aria-label="Close"
                  >
                    <X className="size-4" />
                  </button>
                )}
              </div>
            )}
            {children}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  )
}
