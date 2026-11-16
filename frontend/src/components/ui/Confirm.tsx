import { create } from 'zustand'
import { Modal } from './Modal'
import { Button } from './Button'

interface ConfirmState {
  open: boolean
  title: string
  message?: string
  confirmLabel: string
  danger: boolean
  resolve?: (v: boolean) => void
}

const useConfirmStore = create<ConfirmState>(() => ({ open: false, title: '', confirmLabel: 'Confirm', danger: false }))

export function confirm(opts: { title: string; message?: string; confirmLabel?: string; danger?: boolean }) {
  return new Promise<boolean>((resolve) => {
    useConfirmStore.setState({ open: true, confirmLabel: 'Confirm', danger: false, ...opts, resolve })
  })
}

export function ConfirmHost() {
  const s = useConfirmStore()
  const done = (v: boolean) => {
    s.resolve?.(v)
    useConfirmStore.setState({ open: false })
  }
  return (
    <Modal open={s.open} onClose={() => done(false)} title={s.title} description={s.message} className="sm:max-w-md">
      <div className="flex justify-end gap-2 px-6 pt-2 pb-5">
        <Button variant="ghost" onClick={() => done(false)}>Cancel</Button>
        <Button variant={s.danger ? 'danger' : 'primary'} onClick={() => done(true)} autoFocus>
          {s.confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
