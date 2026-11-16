import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowDown, PanelLeft } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { useConversation } from '@/lib/queries'
import { useSelection } from '@/lib/selection'
import { sendMessage, stopStream, useChat } from '@/store/chat'
import { useUi } from '@/store/ui'
import { Composer, type ComposerHandle } from './Composer'
import { AssistantMessage, StreamingMessage, UserMessage } from './Message'
import { EmptyState } from './EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { WorkspaceSwitcher } from '@/components/layout/WorkspaceSwitcher'
import { ThemePicker } from '@/components/layout/ThemePicker'

export function ChatView() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data, isLoading, isError } = useConversation(id)
  const stream = useChat((s) => (id ? s.streams[id] : undefined))
  const sel = useSelection(data?.conversation)
  const { sidebarOpen, setSidebarOpen, workspaceId, setWorkspaceId } = useUi()
  const composer = useRef<ComposerHandle>(null)
  const scroller = useRef<HTMLDivElement>(null)
  const [atBottom, setAtBottom] = useState(true)

  useEffect(() => {
    if (isError) {
      toast.error('Conversation not found')
      navigate('/mcp', { replace: true })
    }
  }, [isError, navigate])

  // Opening a chat from another workspace switches the workspace with it.
  const convWorkspace = data?.conversation.workspaceId
  useEffect(() => {
    if (convWorkspace && convWorkspace !== workspaceId) setWorkspaceId(convWorkspace)
  }, [convWorkspace]) // eslint-disable-line react-hooks/exhaustive-deps

  // Focus the composer when switching chats.
  useEffect(() => composer.current?.focus(), [id])

  const scrollToBottom = useCallback((smooth = false) => {
    const el = scroller.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }, [])

  useLayoutEffect(() => {
    scrollToBottom()
  }, [id, data?.messages.length, scrollToBottom])

  useEffect(() => {
    if (atBottom) scrollToBottom()
  }, [stream?.parts, atBottom, scrollToBottom])

  const onScroll = () => {
    const el = scroller.current
    if (!el) return
    setAtBottom(el.scrollHeight - el.scrollTop - el.clientHeight < 80)
  }

  const send = async (text: string, regenerate = false) => {
    let convId = id
    if (!convId) {
      try {
        const c = await api.conversations.create({ workspaceId: sel.workspace?.id, providerId: sel.providerId, model: sel.model, serverIds: sel.serverIds })
        convId = c.id
        qc.setQueryData(['conversation', c.id], { conversation: c, messages: [] })
        qc.invalidateQueries({ queryKey: ['conversations'] })
        navigate(`/mcp/c/${c.id}`)
      } catch (e) {
        toast.error('Could not start a chat', { description: (e as Error).message })
        return
      }
    }
    setAtBottom(true)
    sendMessage(qc, convId, {
      content: text,
      workspaceId: sel.workspace?.id,
      providerId: sel.providerId,
      model: sel.model,
      serverIds: sel.serverIds,
      regenerate,
    })
  }

  const messages = data?.messages ?? []
  // While streaming, hide the persisted copy of the turn being (re)generated to avoid duplicates.
  const visible = stream
    ? stream.regenerate
      ? trimTrailingAssistant(messages)
      : messages.at(-1)?.role === 'user' && messages.at(-1)?.content === stream.userText
        ? messages.slice(0, -1)
        : messages
    : messages
  const busy = !!stream
  const lastUser = [...messages].reverse().find((m) => m.role === 'user')
  const empty = !id || (!isLoading && messages.length === 0 && !stream)

  return (
    <div className="relative flex h-full min-w-0 flex-1 flex-col">
      <header className="pointer-events-none absolute inset-x-0 top-0 z-10 flex h-16 items-start gap-2 bg-gradient-to-b from-bg via-bg/90 to-transparent px-3 pt-3 [&>*]:pointer-events-auto">
        {!sidebarOpen && (
          <button
            onClick={() => setSidebarOpen(true)}
            className="ring-focus glass rounded-xl border border-line p-2 text-fg-2 transition hover:text-fg"
            aria-label="Open sidebar"
          >
            <PanelLeft className="size-4" />
          </button>
        )}
        <WorkspaceSwitcher current={sel.workspace} />
        {data && !empty && (
          <div className="glass hidden min-w-0 truncate rounded-xl border border-line px-3 py-1.5 text-sm font-medium text-fg-2 md:block">
            {data.conversation.title}
          </div>
        )}
        <ThemePicker className="ml-auto" />
      </header>

      <div ref={scroller} onScroll={onScroll} className="flex-1 overflow-y-auto">
        {empty ? (
          <EmptyState servers={sel.servers} hasProvider={sel.hasProvider} onPick={(t) => send(t)} />
        ) : isLoading ? (
          <div className="flex h-full items-center justify-center text-muted"><Spinner className="size-6" /></div>
        ) : (
          <div className="w-full space-y-7 px-4 pt-20 pb-10 sm:px-8 lg:px-12">
            {visible.map((m, i) =>
              m.role === 'user' ? (
                <UserMessage key={m.id} content={m.content} />
              ) : (
                <AssistantMessage
                  key={m.id}
                  message={m}
                  isLast={!stream && i === visible.length - 1}
                  busy={busy}
                  onRegenerate={lastUser ? () => send(lastUser.content, true) : undefined}
                />
              ),
            )}
            {stream && (
              <>
                {!stream.regenerate && <UserMessage content={stream.userText} fresh />}
                <StreamingMessage parts={stream.parts} status={stream.status} error={stream.error} />
              </>
            )}
          </div>
        )}
      </div>

      <div className="relative px-3 pb-3 sm:px-6 sm:pb-5">
        <div className="pointer-events-none absolute inset-x-0 -top-10 h-10 bg-gradient-to-t from-bg to-transparent" />
        <AnimatePresence>
          {!atBottom && !empty && (
            <motion.button
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 6 }}
              onClick={() => scrollToBottom(true)}
              className="glass absolute -top-12 left-1/2 z-10 -translate-x-1/2 rounded-full border border-line-strong p-2 text-fg-2 shadow-soft hover:text-fg"
              aria-label="Scroll to bottom"
            >
              <ArrowDown className="size-4" />
            </motion.button>
          )}
        </AnimatePresence>
        <div className="w-full sm:px-2 lg:px-6">
          <Composer
            ref={composer}
            autoFocus
            busy={busy}
            onSend={(t) => send(t)}
            onStop={() => id && stopStream(id)}
            servers={sel.servers}
            serverIds={sel.serverIds}
            onServerIdsChange={sel.setServerIds}
            providers={sel.providers}
            providerId={sel.providerId}
            model={sel.model}
            onModelChange={sel.setModel}
          />
          <p className="mt-2 text-center text-[11px] text-muted/80">
            MVX MCP can make mistakes — verify important figures against the source.
          </p>
        </div>
      </div>
    </div>
  )
}

function trimTrailingAssistant<T extends { role: string }>(list: T[]) {
  let end = list.length
  while (end > 0 && list[end - 1].role === 'assistant') end--
  return list.slice(0, end)
}
