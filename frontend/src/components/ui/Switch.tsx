import { cn } from '@/lib/utils'

export function Switch({ checked, onChange, disabled, size = 'md', label }: {
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
  size?: 'sm' | 'md'
  label?: string
}) {
  const sm = size === 'sm'
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={(e) => {
        e.stopPropagation()
        onChange(!checked)
      }}
      className={cn(
        'ring-focus relative inline-flex shrink-0 cursor-pointer items-center rounded-full border transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50',
        sm ? 'h-[18px] w-8' : 'h-[22px] w-10',
        checked ? 'border-transparent bg-gradient-accent' : 'border-line-strong bg-elev-2',
      )}
    >
      <span
        className={cn(
          'inline-block rounded-full bg-white shadow-sm transition-transform duration-200',
          sm ? 'size-3' : 'size-4',
          checked ? (sm ? 'translate-x-[15px]' : 'translate-x-[20px]') : 'translate-x-[2px]',
        )}
      />
    </button>
  )
}
