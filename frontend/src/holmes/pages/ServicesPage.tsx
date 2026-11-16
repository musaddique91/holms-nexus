import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Boxes,
  FolderGit2,
  GitBranch,
  Pencil,
  Plus,
  Trash2,
  X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Service, ServiceInput } from '../api/types'
import { ErrorNote, PageHeader } from '../components/Page'
import { SecretField } from '../components/SecretField'
import { ListSkeleton } from '../components/Skeleton'
import { toast } from 'sonner'

const EMPTY: ServiceInput = {
  name: '',
  displayName: '',
  aliases: [],
  environment: '',
  repoUrl: '',
  gitProvider: 'GITHUB',
  defaultBranch: 'main',
  codePath: '',
  runbook: '',
  autoFix: false,
  slackChannel: '',
  emailRecipients: [],
  telegramChatId: '',
}

const csv = (v: string) =>
  v
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)

export function ServicesPage() {
  const queryClient = useQueryClient()
  const services = useQuery({ queryKey: ['services'], queryFn: api.services })
  const [editing, setEditing] = useState<{ id: number | null; value: ServiceInput; saved?: Service } | null>(null)

  const save = useMutation({
    mutationFn: ({ id, value }: { id: number | null; value: ServiceInput }) =>
      id === null ? api.createService(value) : api.updateService(id, value),
    onSuccess: (s) => {
      setEditing(null)
      queryClient.invalidateQueries({ queryKey: ['services'] })
      toast.success(`Saved ${s.displayName || s.name}`)
    },
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.removeService(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      toast.success('Service removed')
    },
  })

  const open = (s?: Service) => {
    save.reset()
    setEditing(s ? { id: s.id, value: { ...EMPTY, ...s, gitToken: null }, saved: s } : { id: null, value: { ...EMPTY } })
  }

  return (
    <>
      <PageHeader
        title="Services"
        subtitle="Microservices monitored by Intelligence, repository source mappings, and automated fix policies"
        actions={
          <button className="btn-primary text-xs h-9 px-3.5" onClick={() => open()}>
            <Plus className="size-4" />
            <span>Add Service</span>
          </button>
        }
      />
      <ErrorNote error={services.error ?? remove.error} />

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line bg-subtle/70 text-[11px] font-semibold uppercase tracking-wider text-fg-3">
              <tr>
                <th className="px-5 py-3">Service Name</th>
                <th className="px-5 py-3">Log / Container Names</th>
                <th className="px-5 py-3">Repository & Branch</th>
                <th className="px-5 py-3">Auto-Fix</th>
                <th className="px-5 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/60">
              {services.isLoading && !services.data && (
                <tr>
                  <td colSpan={5} className="p-6">
                    <ListSkeleton rows={4} />
                  </td>
                </tr>
              )}

              {services.data?.map((s) => (
                <tr key={s.id} className="group transition-colors duration-150 hover:bg-subtle/60">
                  <td className="px-5 py-3.5">
                    <div className="font-semibold text-fg">{s.displayName ?? s.name}</div>
                    <div className="font-mono text-xs text-fg-3">{s.name}</div>
                  </td>
                  <td className="px-5 py-3.5 font-mono text-xs text-fg-2">
                    {s.aliases.length > 0 ? (
                      <div className="flex flex-wrap gap-1">
                        {s.aliases.map((a) => (
                          <span key={a} className="rounded bg-subtle px-1.5 py-0.5 border border-line/60">
                            {a}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="text-fg-3">—</span>
                    )}
                  </td>
                  <td className="max-w-xs px-5 py-3.5 text-xs text-fg-2">
                    {s.repoUrl ? (
                      <div className="space-y-0.5">
                        <div className="truncate font-mono font-medium text-fg flex items-center gap-1.5">
                          <FolderGit2 className="size-3.5 text-accent shrink-0" />
                          <span>{s.repoUrl.replace(/^https:\/\/(www\.)?/, '')}</span>
                        </div>
                        <div className="flex items-center gap-2 text-[11px] text-fg-3">
                          <span className="uppercase font-semibold">{s.gitProvider}</span>
                          <span>·</span>
                          <span className="flex items-center gap-1 font-mono">
                            <GitBranch className="size-3" />
                            {s.defaultBranch}
                          </span>
                          {s.codePath && <span>({s.codePath})</span>}
                        </div>
                      </div>
                    ) : (
                      <span className="text-fg-3">No repository configured</span>
                    )}
                  </td>
                  <td className="px-5 py-3.5">
                    <span
                      className={
                        s.autoFix
                          ? 'inline-flex items-center gap-1 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-300'
                          : 'inline-flex items-center gap-1 rounded-full border border-line bg-subtle px-2.5 py-0.5 text-xs font-medium text-fg-3'
                      }
                    >
                      <span className={s.autoFix ? 'size-1.5 rounded-full bg-emerald-500' : 'size-1.5 rounded-full bg-fg-3'} />
                      {s.autoFix ? 'Auto PRs enabled' : 'Suggest only'}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 text-right whitespace-nowrap">
                    <div className="flex items-center justify-end gap-1.5">
                      <button
                        className="btn-secondary h-8 px-2.5 text-xs"
                        onClick={() => open(s)}
                        aria-label={`Edit ${s.name}`}
                      >
                        <Pencil className="size-3.5" />
                        <span>Edit</span>
                      </button>
                      <button
                        className="btn-danger h-8 px-2.5 text-xs"
                        aria-label={`Remove ${s.name}`}
                        onClick={() =>
                          window.confirm(`Remove ${s.name}? Monitored incidents will be preserved.`) &&
                          remove.mutate(s.id)
                        }
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}

              {services.data && services.data.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-5 py-16 text-center">
                    <div className="mx-auto grid size-12 place-items-center rounded-2xl bg-elev-2 text-fg-3 mb-3">
                      <Boxes className="size-6" />
                    </div>
                    <p className="text-sm font-semibold text-fg">No services configured yet</p>
                    <p className="mt-1 text-xs text-fg-3">Add a service to link errors to source repositories.</p>
                    <button className="btn-primary mt-4 text-xs" onClick={() => open()}>
                      <Plus className="size-3.5" /> Add First Service
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {editing && (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-black/50 p-4 backdrop-blur-sm animate-fade-in"
          role="dialog"
          aria-modal="true"
          aria-labelledby="service-dialog-title"
        >
          <form
            className="card max-h-[90vh] w-full max-w-2xl overflow-y-auto p-6 md:p-7 shadow-pop animate-pop-in space-y-6"
            onSubmit={(e) => {
              e.preventDefault()
              save.mutate(editing)
            }}
          >
            <div className="flex items-center justify-between border-b border-line/60 pb-4">
              <div>
                <h2 id="service-dialog-title" className="text-lg font-bold tracking-tight text-fg">
                  {editing.id === null ? 'Add Service' : `Edit ${editing.value.name}`}
                </h2>
                <p className="text-xs text-fg-3">Configure metadata, git repository, and notification channels</p>
              </div>
              <button
                type="button"
                onClick={() => setEditing(null)}
                aria-label="Close"
                className="btn-ghost size-8 p-0"
              >
                <X className="size-4 text-fg-3" />
              </button>
            </div>

            <ServiceForm
              value={editing.value}
              saved={editing.saved}
              onChange={(value) => setEditing({ ...editing, value })}
              onClose={() => setEditing(null)}
            />

            <ErrorNote error={save.error} />

            <div className="flex justify-end gap-2.5 border-t border-line/60 pt-4">
              <button type="button" className="btn-secondary text-xs h-9" onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button type="submit" className="btn-primary text-xs h-9 px-4" disabled={save.isPending}>
                {save.isPending ? 'Saving…' : 'Save Service'}
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  )
}

function ServiceForm({
  value: v,
  saved,
  onChange,
  onClose,
}: {
  value: ServiceInput
  saved?: Service
  onChange: (v: ServiceInput) => void
  onClose: () => void
}) {
  const set = <K extends keyof ServiceInput>(k: K, val: ServiceInput[K]) => onChange({ ...v, [k]: val })
  const [aliasesText, setAliasesText] = useState(v.aliases.join(', '))
  const [emailsText, setEmailsText] = useState(v.emailRecipients.join(', '))

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="space-y-5">
      {/* Basic Settings */}
      <div>
        <h3 className="text-xs font-bold uppercase tracking-wider text-fg-3 mb-3">Service Identification</h3>
        <div className="grid gap-3.5 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="name">Service Key (Internal)</label>
            <input
              id="name"
              autoFocus
              className="input font-mono text-xs"
              required
              pattern="[A-Za-z0-9._-]+"
              value={v.name}
              onChange={(e) => set('name', e.target.value)}
              placeholder="e.g. orders-api"
            />
          </div>
          <div>
            <label className="label" htmlFor="displayName">Display Name</label>
            <input
              id="displayName"
              className="input text-xs"
              value={v.displayName ?? ''}
              onChange={(e) => set('displayName', e.target.value)}
              placeholder="e.g. Maverick HR"
            />
          </div>
          <div className="sm:col-span-2">
            <label className="label" htmlFor="aliases">Telemetry Log / Container Aliases (comma-separated)</label>
            <input
              id="aliases"
              className="input font-mono text-xs"
              value={aliasesText}
              onChange={(e) => {
                setAliasesText(e.target.value)
                set('aliases', csv(e.target.value))
              }}
              placeholder="hr-service, hr-backend, hr-prod"
            />
          </div>
        </div>
      </div>

      {/* Git Integration */}
      <div className="border-t border-line/60 pt-4">
        <h3 className="text-xs font-bold uppercase tracking-wider text-fg-3 mb-3">Repository & Code Base</h3>
        <div className="grid gap-3.5 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="gitProvider">Git Provider</label>
            <select
              id="gitProvider"
              className="input text-xs"
              value={v.gitProvider}
              onChange={(e) => set('gitProvider', e.target.value as ServiceInput['gitProvider'])}
            >
              <option value="GITHUB">GitHub</option>
              <option value="GITLAB">GitLab</option>
              <option value="NONE">None</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="branch">Default Branch</label>
            <input
              id="branch"
              className="input font-mono text-xs"
              value={v.defaultBranch}
              onChange={(e) => set('defaultBranch', e.target.value)}
              placeholder="main"
            />
          </div>
          <div className="sm:col-span-2">
            <label className="label" htmlFor="repoUrl">Repository URL (HTTPS)</label>
            <input
              id="repoUrl"
              className="input font-mono text-xs"
              type="url"
              value={v.repoUrl ?? ''}
              onChange={(e) => set('repoUrl', e.target.value)}
              placeholder="https://github.com/org/repo.git"
            />
          </div>
          {v.gitProvider !== 'NONE' && (
            <SecretField
              id="gitToken"
              className="sm:col-span-2"
              label={<>Personal access token <span className="text-fg-3">(optional)</span></>}
              placeholder={v.gitProvider === 'GITLAB' ? 'glpat-…' : 'github_pat_… or ghp_…'}
              isSet={!!saved?.gitTokenSet}
              hint={saved?.gitTokenHint ?? null}
              value={v.gitToken ?? null}
              onChange={(t) => set('gitToken', t)}
              help={
                v.gitProvider === 'GITLAB'
                  ? 'Used to read this repo and open fix merge requests. Scopes: api, read_repository, write_repository. Empty = the server-wide GITLAB_TOKEN.'
                  : 'Used to read this repo and open fix pull requests. Fine-grained token on this repo with Contents and Pull requests: read & write. Empty = the server-wide GITHUB_TOKEN.'
              }
            />
          )}
          <div>
            <label className="label" htmlFor="codePath">Monorepo Subdirectory</label>
            <input
              id="codePath"
              className="input font-mono text-xs"
              value={v.codePath ?? ''}
              onChange={(e) => set('codePath', e.target.value)}
              placeholder="services/backend"
            />
          </div>
          <div>
            <label className="label" htmlFor="environment">Target Environment</label>
            <input
              id="environment"
              className="input text-xs"
              value={v.environment ?? ''}
              onChange={(e) => set('environment', e.target.value)}
              placeholder="production"
            />
          </div>
        </div>
      </div>

      {/* Auto-fix & Runbook */}
      <div className="border-t border-line/60 pt-4 space-y-3.5">
        <h3 className="text-xs font-bold uppercase tracking-wider text-fg-3">Autonomous Remediation Policy</h3>
        <label className="flex items-start gap-3 rounded-xl border border-line bg-subtle/50 p-3.5 cursor-pointer hover:bg-subtle transition-colors">
          <input
            type="checkbox"
            className="mt-1 size-4 rounded text-accent focus:ring-accent"
            checked={v.autoFix}
            onChange={(e) => set('autoFix', e.target.checked)}
          />
          <div className="text-xs">
            <span className="font-semibold text-fg">Automatically create pull requests for fixes</span>
            <span className="block text-fg-3 mt-0.5">
              When enabled, Intelligence branches off {v.defaultBranch || 'main'} and submits draft PRs with unit tests when confident.
            </span>
          </div>
        </label>

        <div>
          <label className="label" htmlFor="runbook">Runbook & Context for AI Agent</label>
          <textarea
            id="runbook"
            className="input min-h-20 text-xs"
            value={v.runbook ?? ''}
            onChange={(e) => set('runbook', e.target.value)}
            placeholder="Operational notes, expected maintenance windows, database replica topology, downstream dependencies…"
          />
        </div>
      </div>

      {/* Notifications */}
      <div className="border-t border-line/60 pt-4">
        <h3 className="text-xs font-bold uppercase tracking-wider text-fg-3 mb-3">Alert Routing</h3>
        <div className="grid gap-3.5 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="slack">Slack Channel</label>
            <input
              id="slack"
              className="input text-xs"
              value={v.slackChannel ?? ''}
              onChange={(e) => set('slackChannel', e.target.value)}
              placeholder="#alerts-prod"
            />
          </div>
          <div>
            <label className="label" htmlFor="telegram">Telegram Chat ID / Topic</label>
            <input
              id="telegram"
              className="input text-xs"
              value={v.telegramChatId ?? ''}
              onChange={(e) => set('telegramChatId', e.target.value)}
              placeholder="e.g. 10 or -1004432917613:10"
            />
          </div>
          <div className="sm:col-span-2">
            <label className="label" htmlFor="emails">Email Recipients (comma-separated)</label>
            <input
              id="emails"
              className="input text-xs"
              value={emailsText}
              onChange={(e) => {
                setEmailsText(e.target.value)
                set('emailRecipients', csv(e.target.value))
              }}
              placeholder="oncall@example.com, devops@example.com"
            />
          </div>
        </div>
      </div>
    </div>
  )
}
