import { useState } from 'react'
import { Eye, EyeOff, Plus, Trash2 } from 'lucide-react'
import { Input } from './Input'
import { Button } from './Button'

const SECRET_HINT = /(key|token|secret|password|pass|auth|bearer|credential)/i

export function KeyValueEditor({ value, onChange, keyPlaceholder = 'KEY', valuePlaceholder = 'value', addLabel = 'Add' }: {
  value: [string, string][]
  onChange: (v: [string, string][]) => void
  keyPlaceholder?: string
  valuePlaceholder?: string
  addLabel?: string
}) {
  const [revealed, setRevealed] = useState<Record<number, boolean>>({})
  const update = (i: number, k: string, v: string) => onChange(value.map((row, j) => (j === i ? [k, v] : row)))
  return (
    <div className="space-y-2">
      {value.map(([k, v], i) => {
        const secret = SECRET_HINT.test(k) && !revealed[i]
        return (
          <div key={i} className="flex items-center gap-2">
            <Input className="w-2/5 font-mono text-[13px]" placeholder={keyPlaceholder} value={k} onChange={(e) => update(i, e.target.value, v)} />
            <div className="relative flex-1">
              <Input
                className="pr-9 font-mono text-[13px]"
                placeholder={valuePlaceholder}
                type={secret ? 'password' : 'text'}
                value={v}
                onChange={(e) => update(i, k, e.target.value)}
              />
              {SECRET_HINT.test(k) && (
                <button
                  type="button"
                  onClick={() => setRevealed((r) => ({ ...r, [i]: !r[i] }))}
                  className="absolute top-1/2 right-2.5 -translate-y-1/2 text-muted hover:text-fg"
                  aria-label="Toggle visibility"
                >
                  {secret ? <Eye className="size-4" /> : <EyeOff className="size-4" />}
                </button>
              )}
            </div>
            <Button type="button" variant="ghost" size="icon" onClick={() => onChange(value.filter((_, j) => j !== i))} aria-label="Remove">
              <Trash2 className="size-4" />
            </Button>
          </div>
        )
      })}
      <Button type="button" variant="ghost" size="sm" className="text-accent hover:text-accent" onClick={() => onChange([...value, ['', '']])}>
        <Plus className="size-3.5" /> {addLabel}
      </Button>
    </div>
  )
}
