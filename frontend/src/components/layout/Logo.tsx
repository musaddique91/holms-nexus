export function Logo({ size = 28 }: { size?: number }) {
  return (
    <span
      className="relative inline-flex items-center justify-center rounded-[30%] bg-gradient-accent shadow-[0_4px_14px_-4px_var(--accent)]"
      style={{ width: size, height: size }}
    >
      <svg viewBox="0 0 32 32" width={size * 0.62} height={size * 0.62} aria-hidden>
        <path d="M7.5 23V9.5l8.5 9 8.5-9V23" fill="none" stroke="white" strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  )
}

export function Wordmark() {
  return (
    <span className="flex items-center gap-2.5">
      <Logo size={26} />
      <span className="text-[17px] font-semibold tracking-tight">
        holms-nexus<span className="text-gradient">.</span>
      </span>
    </span>
  )
}
