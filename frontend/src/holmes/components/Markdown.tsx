import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/** Renders model output. Raw HTML is never rendered, links open in a new tab. */
export function Markdown({ children }: { children: string | null | undefined }) {
  if (!children) return null
  return (
    <div className="prose-holmes">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        // Model output can be steered by log content; an <img> would make the browser call
        // an attacker-chosen URL the moment the page opens, so images are never rendered.
        disallowedElements={['img']}
        unwrapDisallowed
        components={{
          a: ({ href, children: c }) => (
            <a href={href} target="_blank" rel="noreferrer noopener">
              {c}
            </a>
          ),
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  )
}
