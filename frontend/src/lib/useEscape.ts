import { useEffect, useRef } from 'react'

const stack: symbol[] = []

/** Closes only the top-most open layer (modal, drawer) on Escape. */
export function useEscape(open: boolean, onClose: () => void) {
  const id = useRef(Symbol('layer'))
  const cb = useRef(onClose)
  cb.current = onClose

  useEffect(() => {
    if (!open) return
    const me = id.current
    stack.push(me)
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && stack[stack.length - 1] === me) {
        e.stopPropagation()
        cb.current()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      stack.splice(stack.indexOf(me), 1)
    }
  }, [open])
}
