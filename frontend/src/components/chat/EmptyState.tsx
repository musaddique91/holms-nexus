import { motion } from 'motion/react'
import { ArrowUpRight, KeyRound, Plug, Sparkles } from 'lucide-react'
import type { McpServer } from '@/lib/types'
import { greeting } from '@/lib/utils'
import { ServerIcon } from '@/components/ui/ServerIcon'
import { StatusDot } from '@/components/ui/Badge'
import { useUi } from '@/store/ui'

const SUGGESTIONS: Record<string, string[]> = {
  postgres: ['Give me an overview of the database schema and the biggest tables', 'Which tables grew the most recently? Chart it'],
  sqlite: ['Describe the tables in this database and how they relate', 'Find interesting trends in the data and chart them'],
  redis: ['What kinds of keys are stored and how much memory do they use?'],
  github: ['Summarize my open pull requests and what needs attention', 'Which repos had the most activity this month?'],
  deepwiki: ['How does React’s reconciliation work? Use facebook/react', 'Explain the architecture of spring-projects/spring-ai'],
  context7: ['Show me the latest way to configure routes in Next.js'],
  filesystem: ['List the files in my folder and summarize what they contain'],
  git: ['Summarize the last 20 commits and who made them'],
  fetch: ['Fetch news.ycombinator.com and summarize the top stories'],
  'brave-search': ['Search the web for the latest MCP specification changes'],
  time: ['What time is it now in Tokyo, London and New York?'],
  memory: ['Remember that my team ships on Thursdays, then recall what you know about me'],
  kubernetes: ['Are any pods unhealthy in my cluster? Show restarts in a chart'],
  slack: ['Summarize what happened in #general today'],
  notion: ['Find my most recently edited Notion pages and summarize them'],
  everything: ['Echo “hello MCP” and add 17 + 25 using the tools', 'Show me a sample chart of 6 months of revenue'],
  playwright: ['Open example.com and describe what is on the page'],
}

const GENERIC = [
  'What can you do with my connected data sources?',
  'Show a sample bar chart of monthly sales for 6 months',
]

export function EmptyState({ servers, hasProvider, onPick }: {
  servers: McpServer[]
  hasProvider: boolean
  onPick: (text: string) => void
}) {
  const { openSettings, setAddServerOpen } = useUi()
  const connected = servers.filter((s) => s.enabled)
  const suggestions = Array.from(
    new Set([
      ...connected.flatMap((s) => (s.catalogId && SUGGESTIONS[s.catalogId]) || [`What can you do with ${s.name}?`]),
      ...GENERIC,
    ]),
  ).slice(0, 4)

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col items-center px-4 pt-[12vh] pb-8 text-center">
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
        <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-line bg-elev/60 px-3 py-1 text-xs text-fg-2 backdrop-blur">
          <Sparkles className="size-3.5 text-accent" /> Model Context Protocol workspace
        </div>
        <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl sm:leading-[1.1]">
          {greeting()}. Let’s talk to <span className="text-gradient">your data.</span>
        </h1>
        <p className="mx-auto mt-3 max-w-xl text-[15px] text-muted sm:text-base">
          Ask questions in plain language — MCP calls the tools on your connected MCP servers, fetches the real data and
          explains it with tables and charts.
        </p>
      </motion.div>

      {connected.length > 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.15 }}
          className="mt-6 flex flex-wrap justify-center gap-2"
        >
          {connected.map((s) => (
            <span key={s.id} className="flex items-center gap-2 rounded-full border border-line bg-elev/60 py-1 pr-3 pl-1 text-[13px] backdrop-blur">
              <ServerIcon name={s.name} icon={s.icon} color={s.color} size="xs" className="rounded-full" />
              {s.name}
              <StatusDot status={s.status} />
            </span>
          ))}
        </motion.div>
      )}

      {(!hasProvider || connected.length === 0) && (
        <div className="mt-8 grid w-full gap-3 sm:grid-cols-2">
          {!hasProvider && (
            <SetupCard
              icon={<KeyRound className="size-5" />}
              title="Connect a model"
              body="Add an Anthropic or OpenAI key, or point to a local Ollama."
              onClick={() => openSettings('models')}
            />
          )}
          {connected.length === 0 && (
            <SetupCard
              icon={<Plug className="size-5" />}
              title="Add your first data source"
              body="Pick from the catalog — Postgres, GitHub, files, web and more."
              onClick={() => {
                openSettings('servers')
                setAddServerOpen(true)
              }}
            />
          )}
        </div>
      )}

      <div className="mt-8 grid w-full gap-2.5 sm:grid-cols-2">
        {suggestions.map((s, i) => (
          <motion.button
            key={s}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 + i * 0.06 }}
            onClick={() => onPick(s)}
            className="group flex items-start justify-between gap-3 rounded-2xl border border-line bg-elev/50 px-4 py-3.5 text-left text-sm text-fg-2 backdrop-blur transition hover:-translate-y-0.5 hover:border-accent/30 hover:bg-elev hover:text-fg hover:shadow-soft"
          >
            <span>{s}</span>
            <ArrowUpRight className="size-4 shrink-0 text-muted transition group-hover:text-accent" />
          </motion.button>
        ))}
      </div>
    </div>
  )
}

function SetupCard({ icon, title, body, onClick }: { icon: React.ReactNode; title: string; body: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="group relative overflow-hidden rounded-2xl border border-accent/25 bg-accent-soft p-4 text-left transition hover:border-accent/50"
    >
      <div className="flex items-center gap-3">
        <span className="flex size-10 items-center justify-center rounded-xl bg-gradient-accent text-white">{icon}</span>
        <div>
          <div className="font-semibold">{title}</div>
          <div className="text-sm text-muted">{body}</div>
        </div>
        <ArrowUpRight className="ml-auto size-5 text-accent transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
      </div>
    </button>
  )
}
