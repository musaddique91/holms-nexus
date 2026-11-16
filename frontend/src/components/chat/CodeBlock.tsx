import { useState } from 'react'
import { Check, Copy } from 'lucide-react'
import { cn } from '@/lib/utils'

export function CopyButton({ text, className, label }: { text: string; className?: string; label?: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={() => {
        navigator.clipboard.writeText(text)
        setCopied(true)
        setTimeout(() => setCopied(false), 1400)
      }}
      className={cn('inline-flex items-center gap-1.5 rounded-md px-1.5 py-1 text-xs text-muted transition hover:bg-elev-2 hover:text-fg', className)}
      aria-label="Copy"
    >
      {copied ? <Check className="size-3.5 text-ok" /> : <Copy className="size-3.5" />}
      {label && <span>{copied ? 'Copied' : label}</span>}
    </button>
  )
}

export function CodeBlock({ language, code, children }: { language?: string; code: string; children: React.ReactNode }) {
  return (
    <div className="group/code overflow-hidden rounded-xl border border-line bg-code">
      <div className="flex items-center justify-between border-b border-line px-3 py-1.5">
        <span className="font-mono text-[11px] tracking-wide text-muted uppercase">{language || 'text'}</span>
        <CopyButton text={code} label="Copy" />
      </div>
      <pre className="overflow-x-auto p-4 font-mono text-[13px] leading-relaxed">
        <code className={language ? `hljs language-${language}` : 'hljs'}>{children}</code>
      </pre>
    </div>
  )
}
