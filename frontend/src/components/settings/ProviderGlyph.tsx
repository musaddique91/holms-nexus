import type { ProviderType } from '@/lib/types'
import { cn } from '@/lib/utils'

/** Neutral provider glyphs (no brand marks). */
export function ProviderGlyph({ type, className }: { type: ProviderType; className?: string }) {
  if (type === 'ANTHROPIC') {
    return (
      <svg viewBox="0 0 24 24" className={cn('size-4', className)} fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
        <path d="M12 3v18M3 12h18M5.6 5.6l12.8 12.8M18.4 5.6 5.6 18.4" />
      </svg>
    )
  }
  if (type === 'OPENAI') {
    return (
      <svg viewBox="0 0 24 24" className={cn('size-4', className)} fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
        <path d="M12 2.5 20.2 7.2v9.6L12 21.5l-8.2-4.7V7.2z" />
        <circle cx="12" cy="12" r="3" />
      </svg>
    )
  }
  return (
    <svg viewBox="0 0 24 24" className={cn('size-4', className)} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <rect x="4" y="4" width="16" height="16" rx="5" />
      <path d="M9 10v.01M15 10v.01M9 15c1.7 1.3 4.3 1.3 6 0" />
    </svg>
  )
}

export const providerLabel: Record<ProviderType, string> = {
  ANTHROPIC: 'Anthropic',
  OPENAI: 'OpenAI-compatible',
  OLLAMA: 'Ollama',
}
