import { memo, isValidElement, type ReactNode } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeHighlight from 'rehype-highlight'
import { CodeBlock } from './CodeBlock'
import { RichBlock } from '@/components/data/RichBlock'

function textOf(node: ReactNode): string {
  if (node == null || typeof node === 'boolean') return ''
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(textOf).join('')
  if (isValidElement(node)) return textOf((node.props as { children?: ReactNode }).children)
  return ''
}

function makeComponents(streaming: boolean): Components {
  return {
    pre({ children }) {
      const child = Array.isArray(children) ? children[0] : children
      if (isValidElement(child)) {
        const props = child.props as { className?: string; children?: ReactNode }
        const lang = /language-([\w-]+)/.exec(props.className || '')?.[1]
        const code = textOf(props.children).replace(/\n$/, '')
        if (lang === 'chart' || lang === 'highcharts' || lang === 'datatable') return <RichBlock lang={lang} source={code} streaming={streaming} />
        return <CodeBlock language={lang} code={code}>{props.children}</CodeBlock>
      }
      return <pre>{children}</pre>
    },
    table({ children }) {
      return (
        <div className="overflow-x-auto rounded-xl border border-line">
          <table>{children}</table>
        </div>
      )
    },
    a({ href, children }) {
      return <a href={href} target="_blank" rel="noreferrer noopener">{children}</a>
    },
  }
}

const staticComponents = makeComponents(false)
const streamingComponents = makeComponents(true)

export const Markdown = memo(function Markdown({ text, streaming = false }: { text: string; streaming?: boolean }) {
  return (
    <div className="md">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeHighlight, { detect: false, ignoreMissing: true, plainText: ['chart', 'highcharts', 'datatable'] }]]}
        components={streaming ? streamingComponents : staticComponents}
      >
        {text}
      </ReactMarkdown>
    </div>
  )
})
