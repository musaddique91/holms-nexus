import { useMutation, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CheckCircle2, ChevronDown, Loader2, Mail, MessageSquare, Save, Send, XCircle } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Channel, MinSeverity, NotificationSettings, NotificationSettingsUpdate, NotificationTestResult } from '../api/types'
import { SecretField } from '../components/SecretField'
import { toast } from 'sonner'
import { timeAgo } from '../lib/format'

const SEVERITIES: { id: MinSeverity; label: string; note: string }[] = [
  { id: 'CRITICAL', label: 'Critical only', note: 'Outages' },
  { id: 'HIGH', label: 'High +', note: 'Critical and high' },
  { id: 'MEDIUM', label: 'Medium +', note: 'Skips low' },
  { id: 'LOW', label: 'Everything', note: 'Every incident' },
]

const SMTP_PRESETS = [
  { name: 'Gmail', host: 'smtp.gmail.com', port: 587 },
  { name: 'Outlook / 365', host: 'smtp.office365.com', port: 587 },
  { name: 'SendGrid', host: 'smtp.sendgrid.net', port: 587 },
  { name: 'Amazon SES', host: 'email-smtp.ap-south-1.amazonaws.com', port: 587 },
]

/** Non-secret fields, edited in place. Secrets live separately: null = keep the saved one. */
interface Form {
  minSeverity: MinSeverity
  slackEnabled: boolean
  slackMode: 'webhook' | 'bot'
  slackChannel: string
  emailEnabled: boolean
  smtpHost: string
  smtpPort: number
  smtpUsername: string
  startTls: boolean
  from: string
  to: string
  telegramEnabled: boolean
  telegramChatId: string
}

interface Secrets {
  slackWebhook: string | null
  slackBotToken: string | null
  smtpPassword: string | null
  telegramToken: string | null
}

const NO_SECRET_CHANGES: Secrets = { slackWebhook: null, slackBotToken: null, smtpPassword: null, telegramToken: null }

const fromSettings = (s: NotificationSettings): Form => ({
  minSeverity: s.minSeverity,
  slackEnabled: s.slack.enabled,
  slackMode: s.slack.botTokenSet && !s.slack.webhookSet ? 'bot' : 'webhook',
  slackChannel: s.slack.channel ?? '',
  emailEnabled: s.email.enabled,
  smtpHost: s.email.smtpHost ?? '',
  smtpPort: s.email.smtpPort,
  smtpUsername: s.email.smtpUsername ?? '',
  startTls: s.email.startTls,
  from: s.email.from ?? '',
  to: s.email.to.join(', '),
  telegramEnabled: s.telegram.enabled,
  telegramChatId: s.telegram.chatId ?? '',
})

export function NotificationsCard({ saved }: { saved: NotificationSettings }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<Form>(() => fromSettings(saved))
  const [secrets, setSecrets] = useState<Secrets>(NO_SECRET_CHANGES)
  const [tests, setTests] = useState<Partial<Record<Channel, NotificationTestResult>>>({})

  useEffect(() => setForm(fromSettings(saved)), [saved])

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }))
  const setSecret = (k: keyof Secrets, v: string | null) => setSecrets((s) => ({ ...s, [k]: v }))

  const payload = (): NotificationSettingsUpdate => ({
    minSeverity: form.minSeverity,
    slack: {
      enabled: form.slackEnabled,
      channel: form.slackChannel,
      webhookUrl: secrets.slackWebhook,
      botToken: secrets.slackBotToken,
    },
    email: {
      enabled: form.emailEnabled,
      smtpHost: form.smtpHost,
      smtpPort: Number(form.smtpPort) || 587,
      smtpUsername: form.smtpUsername,
      smtpPassword: secrets.smtpPassword,
      startTls: form.startTls,
      from: form.from,
      to: form.to.split(/[,;\s]+/).map((t) => t.trim()).filter(Boolean),
    },
    telegram: { enabled: form.telegramEnabled, chatId: form.telegramChatId, botToken: secrets.telegramToken },
  })

  const save = useMutation({
    mutationFn: () => api.saveNotificationSettings(payload()),
    onSuccess: (s) => {
      queryClient.setQueryData(['notification-settings'], s)
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setSecrets(NO_SECRET_CHANGES)
      const on = s.channels.filter((c) => c.switchedOn && !c.missing).map((c) => LABEL[c.channel])
      toast.success('Notifications saved', { description: on.length ? `Sending to ${on.join(', ')}.` : 'All channels are off.' })
    },
    onError: (e) => toast.error('Could not save', { description: e instanceof Error ? e.message : undefined }),
  })

  const test = useMutation({
    mutationFn: (channel: Channel) => api.testNotification(channel, payload()),
    onMutate: (channel) => setTests((t) => ({ ...t, [channel]: undefined })),
    onSuccess: (r) => setTests((t) => ({ ...t, [r.channel]: r })),
    onError: (e, channel) =>
      setTests((t) => ({ ...t, [channel]: { ok: false, channel, latencyMs: 0, detail: e instanceof Error ? e.message : 'Failed' } })),
  })

  const dirty =
    JSON.stringify(form) !== JSON.stringify(fromSettings(saved)) || Object.values(secrets).some((v) => v !== null)
  const status = (c: Channel) => saved.channels.find((x) => x.channel === c)
  const testing = (c: Channel) => test.isPending && test.variables === c

  return (
    <section className="card p-5 md:p-6" aria-labelledby="notif-title">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="notif-title" className="text-base font-semibold text-fg">Notifications</h2>
          <p className="mt-0.5 text-sm text-fg-3">
            Where Intelligence sends each analysed incident. Enable any combination of channels.{' '}
            {saved.source === 'environment'
              ? 'Currently using the server environment — saving moves these settings into the database.'
              : saved.updatedAt && <>Last changed {timeAgo(saved.updatedAt)}{saved.updatedBy ? ` by ${saved.updatedBy}` : ''}.</>}
          </p>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {saved.channels.map((c) => (
            <span key={c.channel} className="chip">
              <span className={clsx('size-1.5 rounded-full', c.switchedOn && !c.missing ? 'bg-emerald-500' : c.switchedOn ? 'bg-amber-500' : 'bg-fg-3/50')} />
              {LABEL[c.channel]}
            </span>
          ))}
        </div>
      </div>

      <fieldset>
        <legend className="label">Notify for</legend>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4" role="radiogroup">
          {SEVERITIES.map((s) => (
            <button
              key={s.id}
              type="button"
              role="radio"
              aria-checked={form.minSeverity === s.id}
              onClick={() => set('minSeverity', s.id)}
              className={clsx(
                'rounded-xl border p-3 text-left transition',
                form.minSeverity === s.id ? 'border-accent bg-accent-soft ring-2 ring-accent/20' : 'border-line hover:bg-elev-2',
              )}
            >
              <div className="text-sm font-medium text-fg">{s.label}</div>
              <div className="mt-0.5 text-xs text-fg-3">{s.note}</div>
            </button>
          ))}
        </div>
      </fieldset>

      <div className="mt-6 space-y-3">
        <ChannelPanel
          icon={<MessageSquare className="size-5" />}
          tint="bg-violet-500/12 text-violet-600 dark:text-violet-300"
          title="Slack"
          subtitle={form.slackMode === 'webhook' ? 'Incoming webhook — posts to one channel' : 'Bot token — per-service channels'}
          on={form.slackEnabled}
          onToggle={(v) => set('slackEnabled', v)}
          missing={status('slack')?.missing ?? null}
          result={tests.slack}
          testing={testing('slack')}
          onTest={() => test.mutate('slack')}
        >
          <div className="sm:col-span-2">
            <div className="inline-flex rounded-xl border border-line bg-subtle p-0.5 text-sm" role="tablist">
              {(['webhook', 'bot'] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  role="tab"
                  aria-selected={form.slackMode === m}
                  onClick={() => set('slackMode', m)}
                  className={clsx('rounded-[10px] px-3 py-1.5 transition', form.slackMode === m ? 'bg-surface font-medium text-fg shadow-sm' : 'text-fg-3 hover:text-fg')}
                >
                  {m === 'webhook' ? 'Incoming webhook' : 'Bot token'}
                </button>
              ))}
            </div>
          </div>
          {form.slackMode === 'webhook' ? (
            <SecretField
              id="slackWebhook"
              className="sm:col-span-2"
              label="Webhook URL"
              placeholder="https://hooks.slack.com/services/…"
              isSet={saved.slack.webhookSet}
              hint={saved.slack.webhookHint}
              value={secrets.slackWebhook}
              onChange={(v) => setSecret('slackWebhook', v)}
              help="Slack → Apps → Incoming Webhooks → Add to a channel."
            />
          ) : (
            <>
              <SecretField
                id="slackBot"
                label="Bot token"
                placeholder="xoxb-…"
                isSet={saved.slack.botTokenSet}
                hint={saved.slack.botTokenHint}
                value={secrets.slackBotToken}
                onChange={(v) => setSecret('slackBotToken', v)}
                help="Needs the chat:write scope; invite the bot to each channel."
              />
              <Field id="slackChannel" label="Default channel" value={form.slackChannel} onChange={(v) => set('slackChannel', v)} placeholder="#alerts" help="A service's own channel (Services page) wins." />
            </>
          )}
        </ChannelPanel>

        <ChannelPanel
          icon={<Mail className="size-5" />}
          tint="bg-sky-500/12 text-sky-600 dark:text-sky-300"
          title="Email"
          subtitle="HTML e-mail over your SMTP server"
          on={form.emailEnabled}
          onToggle={(v) => set('emailEnabled', v)}
          missing={status('email')?.missing ?? null}
          result={tests.email}
          testing={testing('email')}
          onTest={() => test.mutate('email')}
        >
          <div className="flex flex-wrap items-center gap-1.5 sm:col-span-2">
            <span className="text-xs text-fg-3">Presets</span>
            {SMTP_PRESETS.map((p) => (
              <button
                key={p.name}
                type="button"
                className={clsx('chip transition hover:bg-elev-2', form.smtpHost === p.host && 'border-accent text-accent')}
                onClick={() => setForm((f) => ({ ...f, smtpHost: p.host, smtpPort: p.port, startTls: true }))}
              >
                {p.name}
              </button>
            ))}
          </div>
          <Field id="smtpHost" label="SMTP server" value={form.smtpHost} onChange={(v) => set('smtpHost', v)} placeholder="smtp.gmail.com" mono />
          <div className="grid grid-cols-[1fr_auto] items-end gap-3">
            <Field id="smtpPort" label="Port" value={String(form.smtpPort)} onChange={(v) => set('smtpPort', Number(v.replace(/\D/g, '')) || 0)} placeholder="587" mono />
            <label className="mb-2.5 flex items-center gap-2 text-sm text-fg-2">
              <input type="checkbox" className="size-4 rounded text-accent" checked={form.startTls} onChange={(e) => set('startTls', e.target.checked)} />
              STARTTLS
            </label>
          </div>
          <Field id="smtpUser" label="Username" value={form.smtpUsername} onChange={(v) => set('smtpUsername', v)} placeholder="alerts@example.com" autoComplete="off" />
          <SecretField
            id="smtpPassword"
            label="Password"
            placeholder="App password"
            isSet={saved.email.smtpPasswordSet}
            hint={saved.email.smtpPasswordSet ? '••••••••' : null}
            value={secrets.smtpPassword}
            onChange={(v) => setSecret('smtpPassword', v)}
            help="For Gmail use an App Password, not the account password."
          />
          <Field id="from" label="From" value={form.from} onChange={(v) => set('from', v)} placeholder="holms-nexus <alerts@example.com>" />
          <Field id="to" label="Recipients" value={form.to} onChange={(v) => set('to', v)} placeholder="ops@example.com, you@example.com" help="Comma-separated. A service's own recipients are added." />
        </ChannelPanel>

        <ChannelPanel
          icon={<Send className="size-5" />}
          tint="bg-cyan-500/12 text-cyan-600 dark:text-cyan-300"
          title="Telegram"
          subtitle="Bot message to a chat, group or channel"
          on={form.telegramEnabled}
          onToggle={(v) => set('telegramEnabled', v)}
          missing={status('telegram')?.missing ?? null}
          result={tests.telegram}
          testing={testing('telegram')}
          onTest={() => test.mutate('telegram')}
        >
          <SecretField
            id="telegramToken"
            label="Bot token"
            placeholder="123456789:AA…"
            isSet={saved.telegram.botTokenSet}
            hint={saved.telegram.botTokenHint}
            value={secrets.telegramToken}
            onChange={(v) => setSecret('telegramToken', v)}
            help="Create a bot with @BotFather and copy its token."
          />
          <Field id="telegramChat" label="Chat id" value={form.telegramChatId} onChange={(v) => set('telegramChatId', v)} placeholder="-1001234567890" mono help="Add the bot to the group, then get the id from @userinfobot." />
        </ChannelPanel>
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-end gap-2">
        <p className="mr-auto text-xs text-fg-3">“Send test” uses what is on screen — no need to save first.</p>
        {dirty && (
          <button type="button" className="btn-ghost" onClick={() => { setForm(fromSettings(saved)); setSecrets(NO_SECRET_CHANGES); setTests({}) }}>
            Discard changes
          </button>
        )}
        <button type="button" className="btn-primary" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />} Save
        </button>
      </div>
    </section>
  )
}

const LABEL: Record<Channel, string> = { slack: 'Slack', email: 'Email', telegram: 'Telegram' }

function ChannelPanel(props: {
  icon: ReactNode
  tint: string
  title: string
  subtitle: string
  on: boolean
  onToggle: (v: boolean) => void
  missing: string | null
  result?: NotificationTestResult
  testing: boolean
  onTest: () => void
  children: ReactNode
}) {
  const [open, setOpen] = useState(props.on)
  const id = `panel-${props.title.toLowerCase()}`
  return (
    <div className={clsx('rounded-2xl border transition', props.on ? 'border-accent/40 bg-accent-soft/30' : 'border-line')}>
      <div className="flex items-center gap-3 p-4">
        <span className={clsx('grid size-10 shrink-0 place-items-center rounded-xl', props.tint)}>{props.icon}</span>
        <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-controls={id}>
          <div className="flex items-center gap-2 text-sm font-semibold text-fg">
            {props.title}
            <ChevronDown className={clsx('size-4 text-fg-3 transition', open && 'rotate-180')} />
          </div>
          <div className="truncate text-xs text-fg-3">
            {props.on && props.missing ? <span className="text-amber-700 dark:text-amber-300">{props.missing}</span> : props.subtitle}
          </div>
        </button>
        <Switch
          checked={props.on}
          onChange={(v) => {
            props.onToggle(v)
            if (v) setOpen(true)
          }}
          label={`Send notifications to ${props.title}`}
        />
      </div>
      {open && (
        <div id={id} className="border-t border-line p-4">
          <div className="grid gap-4 sm:grid-cols-2">{props.children}</div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button type="button" className="btn-secondary h-9" disabled={props.testing} onClick={props.onTest}>
              {props.testing ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />} Send test
            </button>
            {props.result && (
              <p
                role="status"
                className={clsx('flex min-w-0 items-start gap-1.5 text-sm', props.result.ok ? 'text-emerald-700 dark:text-emerald-300' : 'text-red-700 dark:text-red-300')}
              >
                {props.result.ok ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : <XCircle className="mt-0.5 size-4 shrink-0" />}
                <span className="break-words">{props.result.detail}</span>
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function Switch({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={clsx(
        'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors focus-visible:ring-2 focus-visible:ring-accent/40',
        checked ? 'bg-accent' : 'bg-fg-3/30',
      )}
    >
      <span className={clsx('inline-block size-5 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-6' : 'translate-x-1')} />
    </button>
  )
}

function Field(props: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  placeholder?: string
  help?: string
  mono?: boolean
  autoComplete?: string
}) {
  return (
    <div>
      <label className="label" htmlFor={props.id}>{props.label}</label>
      <input
        id={props.id}
        className={clsx('input', props.mono && 'font-mono')}
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
        placeholder={props.placeholder}
        autoComplete={props.autoComplete}
      />
      {props.help && <p className="mt-1.5 text-xs text-fg-3">{props.help}</p>}
    </div>
  )
}
