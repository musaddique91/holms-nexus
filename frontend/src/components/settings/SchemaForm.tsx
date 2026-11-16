import type { JsonSchema } from '@/lib/types'
import { Field, Input, Textarea } from '@/components/ui/Input'
import { Switch } from '@/components/ui/Switch'

const typeOf = (s: JsonSchema) => (Array.isArray(s.type) ? s.type.find((t) => t !== 'null') : s.type) ?? 'string'

/** Renders inputs for a tool's JSON schema; complex types fall back to JSON text. Values are kept as raw strings/booleans. */
export function SchemaForm({ schema, values, onChange }: {
  schema?: JsonSchema
  values: Record<string, unknown>
  onChange: (v: Record<string, unknown>) => void
}) {
  const props = schema?.properties ?? {}
  const required = new Set(schema?.required ?? [])
  const keys = Object.keys(props)
  if (!keys.length) return <p className="text-sm text-muted">This tool takes no arguments.</p>
  const set = (k: string, v: unknown) => onChange({ ...values, [k]: v })
  return (
    <div className="space-y-3">
      {keys.map((k) => {
        const p = props[k]
        const t = typeOf(p)
        const label = (
          <span className="flex items-center gap-2">
            <span className="font-mono">{k}</span>
            <span className="text-xs font-normal text-muted">{t}</span>
          </span>
        )
        if (p.enum) {
          return (
            <Field key={k} label={label} hint={p.description} required={required.has(k)}>
              <select
                value={String(values[k] ?? '')}
                onChange={(e) => set(k, e.target.value)}
                className="h-9 w-full rounded-xl border border-line bg-elev px-3 text-sm outline-none focus:border-accent/60"
              >
                <option value="">—</option>
                {p.enum.map((o) => <option key={String(o)} value={String(o)}>{String(o)}</option>)}
              </select>
            </Field>
          )
        }
        if (t === 'boolean') {
          return (
            <div key={k} className="flex items-center justify-between gap-4 rounded-xl border border-line px-3 py-2.5">
              <div>
                <div className="text-[13px] font-medium">{label}</div>
                {p.description && <div className="text-xs text-muted">{p.description}</div>}
              </div>
              <Switch checked={!!values[k]} onChange={(v) => set(k, v)} />
            </div>
          )
        }
        if (t === 'object' || t === 'array') {
          return (
            <Field key={k} label={label} hint={p.description ?? 'JSON value'} required={required.has(k)}>
              <Textarea className="min-h-[72px] font-mono text-[13px]" value={String(values[k] ?? '')} onChange={(e) => set(k, e.target.value)} placeholder={t === 'array' ? '[]' : '{}'} />
            </Field>
          )
        }
        return (
          <Field key={k} label={label} hint={p.description} required={required.has(k)}>
            <Input
              type={t === 'number' || t === 'integer' ? 'number' : 'text'}
              value={String(values[k] ?? '')}
              onChange={(e) => set(k, e.target.value)}
              placeholder={p.default != null ? String(p.default) : undefined}
            />
          </Field>
        )
      })}
    </div>
  )
}

/** Converts raw form values into typed tool arguments. Throws on invalid JSON. */
export function coerceArgs(schema: JsonSchema | undefined, values: Record<string, unknown>) {
  const out: Record<string, unknown> = {}
  for (const [k, p] of Object.entries(schema?.properties ?? {})) {
    const v = values[k]
    if (v === undefined || v === '') continue
    const t = typeOf(p)
    if (t === 'number') out[k] = Number(v)
    else if (t === 'integer') out[k] = parseInt(String(v), 10)
    else if (t === 'object' || t === 'array') out[k] = JSON.parse(String(v))
    else out[k] = v
  }
  return out
}
