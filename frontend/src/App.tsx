import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import { formatTime } from './formatTime'
import './App.css'

interface RcaCardData {
  rootCause: string
  severity: string
  evidence: string[]
  snippets: { filePath: string; lineNumber: number; snippet: string }[]
  recommendations: string[]
  commits: { commitId: string; author: string; message: string }[]
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  quickReplies?: string[]
  action?: string | null
  rca?: RcaCardData | null
}

const STORAGE_KEY = 'rca-agent-chat-v1'

const INITIAL_MESSAGE: Message = {
  role: 'assistant',
  content:
    "Hey! I'm your RCA Agent 🔍\n\nI investigate production issues using logs and git history, then can open an auto-fix PR when you're ready.\n\n**Try this:** describe the symptom, paste error logs, or tap a starter below.",
  timestamp: new Date().toISOString(),
  quickReplies: ['🔍 Investigate an issue', '📋 Paste logs']
}

const LOADING_STEPS = [
  'Reading your description…',
  'Correlating logs & signals…',
  'Scanning recent commits…',
  'Drafting root cause…'
]

const GITHUB_TOKEN_RE = /\b(ghp_[A-Za-z0-9_]{8,}|github_pat_[A-Za-z0-9_]{8,})\b/g

function maskSecrets(text: string): string {
  return text.replace(GITHUB_TOKEN_RE, (match) => {
    const prefix = match.startsWith('github_pat_') ? 'github_pat_' : 'ghp_'
    return `${prefix}${'•'.repeat(12)}`
  })
}

function severityClass(severity: string): string {
  const s = (severity || '').toUpperCase()
  if (s.includes('CRIT')) return 'sev-critical'
  if (s.includes('HIGH')) return 'sev-high'
  if (s.includes('LOW')) return 'sev-low'
  return 'sev-medium'
}

function loadPersisted(): { messages: Message[]; sessionId: string | null } | null {
  try {
    const raw = globalThis.localStorage?.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { messages?: Message[]; sessionId?: string | null }
    if (!parsed.messages?.length) return null
    return { messages: parsed.messages, sessionId: parsed.sessionId ?? null }
  } catch {
    return null
  }
}

function persistState(messages: Message[], sessionId: string | null) {
  try {
    globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify({ messages, sessionId }))
  } catch {
    // ignore quota / private mode
  }
}

function rcaToMarkdown(rca: RcaCardData): string {
  const lines = [`# RCA — ${rca.severity}`, '', '## Root cause', rca.rootCause, '']
  if (rca.evidence?.length) {
    lines.push('## Evidence', ...rca.evidence.map((e) => `- ${e}`), '')
  }
  if (rca.recommendations?.length) {
    lines.push('## Recommendations', ...rca.recommendations.map((r) => `- ${r}`), '')
  }
  if (rca.snippets?.length) {
    lines.push('## Code')
    for (const s of rca.snippets) {
      lines.push(`### ${s.filePath}:${s.lineNumber}`, '```', s.snippet, '```', '')
    }
  }
  if (rca.commits?.length) {
    lines.push('## Related commits')
    for (const c of rca.commits) {
      lines.push(`- \`${c.commitId}\` ${c.message} (${c.author})`)
    }
  }
  return lines.join('\n')
}

function App() {
  const persisted = useMemo(() => loadPersisted(), [])
  const [messages, setMessages] = useState<Message[]>(
    () => persisted?.messages ?? [{ ...INITIAL_MESSAGE, timestamp: new Date().toISOString() }]
  )
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingStep, setLoadingStep] = useState(0)
  const [sessionId, setSessionId] = useState<string | null>(() => persisted?.sessionId ?? null)
  const [showScrollBtn, setShowScrollBtn] = useState(false)
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const chatContainerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const lastFailedMessageRef = useRef<string | null>(null)
  const sessionIdRef = useRef<string | null>(sessionId)

  useEffect(() => {
    sessionIdRef.current = sessionId
  }, [sessionId])

  useEffect(() => {
    persistState(messages, sessionId)
  }, [messages, sessionId])

  useEffect(() => {
    if (!loading) {
      setLoadingStep(0)
      return
    }
    const id = window.setInterval(() => {
      setLoadingStep((step) => (step + 1) % LOADING_STEPS.length)
    }, 2200)
    return () => window.clearInterval(id)
  }, [loading])

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, loading, scrollToBottom])

  useEffect(() => {
    const container = chatContainerRef.current
    if (!container) return
    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container
      setShowScrollBtn(scrollHeight - scrollTop - clientHeight > 100)
    }
    container.addEventListener('scroll', handleScroll)
    return () => container.removeEventListener('scroll', handleScroll)
  }, [])

  useEffect(() => {
    const textarea = inputRef.current
    if (!textarea) return
    textarea.style.height = '42px'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 150)}px`
  }, [input])

  const copyText = async (key: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopiedKey(key)
      window.setTimeout(() => setCopiedKey((current) => (current === key ? null : current)), 1500)
    } catch {
      // ignore
    }
  }

  const sendMessage = async (text?: string) => {
    const messageText = text || input.trim()
    if (!messageText || loading) return

    const userMessage: Message = {
      role: 'user',
      content: messageText,
      timestamp: new Date().toISOString()
    }
    setMessages((prev) => [...prev.map((m) => ({ ...m, quickReplies: undefined })), userMessage])
    setInput('')
    setLoading(true)

    try {
      const response = await fetch('/api/v1/rca/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: messageText, sessionId: sessionIdRef.current })
      })

      if (!response.ok) {
        const detail = await response.text().catch(() => '')
        throw new Error(
          detail?.trim()
            ? `Request failed (${response.status}): ${detail.trim().slice(0, 200)}`
            : `Request failed (${response.status})`
        )
      }

      const data = await response.json()
      setSessionId(data.sessionId)
      lastFailedMessageRef.current = null

      const assistantMessage: Message = {
        role: 'assistant',
        content: data.message,
        timestamp: new Date().toISOString(),
        quickReplies: data.quickReplies?.length > 0 ? data.quickReplies : undefined,
        action: data.action ?? null,
        rca: data.rca ?? null
      }
      setMessages((prev) => [...prev, assistantMessage])
    } catch (err) {
      lastFailedMessageRef.current = messageText
      const detail =
        err instanceof Error && err.message && !err.message.startsWith('Failed to fetch')
          ? err.message
          : 'Something went wrong. Please try again.'
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `❌ ${detail}`,
          timestamp: new Date().toISOString(),
          quickReplies: ['🔄 Try again']
        }
      ])
    } finally {
      setLoading(false)
      inputRef.current?.focus()
    }
  }

  const handleQuickReply = (reply: string) => {
    if (reply === '🔄 Try again' && lastFailedMessageRef.current) {
      const failed = lastFailedMessageRef.current
      setMessages((prev) => {
        const next = [...prev]
        while (
          next.length > 0 &&
          next[next.length - 1].role === 'assistant' &&
          next[next.length - 1].content.startsWith('❌')
        ) {
          next.pop()
        }
        if (next.length > 0 && next[next.length - 1].role === 'user') {
          next.pop()
        }
        return next
      })
      void sendMessage(failed)
      return
    }
    void sendMessage(reply)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void sendMessage()
    }
  }

  const newSession = () => {
    const current = sessionIdRef.current
    if (current) {
      void fetch(`/api/v1/rca/chat/${encodeURIComponent(current)}`, { method: 'DELETE' }).catch(
        () => undefined
      )
    }
    lastFailedMessageRef.current = null
    try {
      globalThis.localStorage?.removeItem(STORAGE_KEY)
    } catch {
      // ignore missing Storage in tests / private mode
    }
    setMessages([{ ...INITIAL_MESSAGE, timestamp: new Date().toISOString() }])
    setSessionId(null)
    setInput('')
    inputRef.current?.focus()
  }

  return (
    <div className="app">
      <header className="header">
        <div className="header-content">
          <span className="logo" aria-hidden="true">
            🔍
          </span>
          <h1>RCA Agent</h1>
          <span className="subtitle">Root Cause Analysis Assistant</span>
          <button
            className="new-session-btn"
            onClick={newSession}
            aria-label="Start new conversation"
            title="New conversation"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </button>
        </div>
      </header>

      <main className="chat-container" ref={chatContainerRef} role="log" aria-live="polite">
        <div className="messages">
          {messages.length <= 1 && (
            <div className="onboarding" aria-label="Getting started tips">
              <div className="onboarding-card">
                <strong>1. Describe the incident</strong>
                <span>Symptom, service, and when it started</span>
              </div>
              <div className="onboarding-card">
                <strong>2. Add evidence</strong>
                <span>Paste logs or stack traces for stronger RCA</span>
              </div>
              <div className="onboarding-card">
                <strong>3. Review &amp; fix</strong>
                <span>Inspect the RCA card, then request an auto-fix PR</span>
              </div>
            </div>
          )}

          {messages.map((msg, i) => (
            <div key={i} className={`message ${msg.role}`}>
              <div className="avatar" aria-hidden="true">
                {msg.role === 'assistant' ? '🤖' : '👤'}
              </div>
              <div className="message-content">
                {msg.rca ? (
                  <div className="rca-card" data-testid="rca-card">
                    <div className="rca-card-header">
                      <span className={`severity-badge ${severityClass(msg.rca.severity)}`}>
                        {msg.rca.severity || 'UNKNOWN'}
                      </span>
                      <span className="rca-card-title">Root Cause Analysis</span>
                      <div className="rca-card-actions">
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() => void copyText(`rca-${i}`, rcaToMarkdown(msg.rca!))}
                          aria-label="Copy RCA as Markdown"
                        >
                          {copiedKey === `rca-${i}` ? 'Copied' : 'Copy'}
                        </button>
                        <button
                          type="button"
                          className="chip-btn"
                          onClick={() =>
                            void copyText(`rca-json-${i}`, JSON.stringify(msg.rca, null, 2))
                          }
                          aria-label="Copy RCA as JSON"
                        >
                          {copiedKey === `rca-json-${i}` ? 'Copied' : 'JSON'}
                        </button>
                      </div>
                    </div>
                    <p className="rca-root-cause">{maskSecrets(msg.rca.rootCause)}</p>
                    {msg.rca.evidence?.length > 0 && (
                      <div className="rca-section">
                        <h4>Evidence</h4>
                        <ul>
                          {msg.rca.evidence.map((item, idx) => (
                            <li key={idx}>{maskSecrets(item)}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {msg.rca.recommendations?.length > 0 && (
                      <div className="rca-section">
                        <h4>Recommendations</h4>
                        <ul>
                          {msg.rca.recommendations.map((item, idx) => (
                            <li key={idx}>{item}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {msg.rca.snippets?.length > 0 && (
                      <div className="rca-section">
                        <h4>Code</h4>
                        {msg.rca.snippets.map((snippet, idx) => (
                          <div key={idx} className="snippet-block">
                            <div className="snippet-meta">
                              <code>
                                {snippet.filePath}:{snippet.lineNumber}
                              </code>
                              <button
                                type="button"
                                className="copy-btn"
                                onClick={() => void copyText(`snip-${i}-${idx}`, snippet.snippet)}
                                aria-label="Copy snippet"
                              >
                                {copiedKey === `snip-${i}-${idx}` ? '✓' : '📋'}
                              </button>
                            </div>
                            <pre>
                              <code>{snippet.snippet}</code>
                            </pre>
                          </div>
                        ))}
                      </div>
                    )}
                    {msg.rca.commits?.length > 0 && (
                      <div className="rca-section">
                        <h4>Related commits</h4>
                        <ul className="commit-list">
                          {msg.rca.commits.map((commit, idx) => (
                            <li key={idx}>
                              <code>{commit.commitId}</code> {commit.message}{' '}
                              <span className="muted">({commit.author})</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="message-bubble">
                    {msg.role === 'assistant' ? (
                      <ReactMarkdown
                        components={{
                          code({ className, children, ...props }) {
                            const isBlock = className?.includes('language-')
                            if (isBlock) {
                              return (
                                <div className="code-block-wrapper">
                                  <button
                                    className="copy-btn"
                                    onClick={() =>
                                      navigator.clipboard.writeText(String(children))
                                    }
                                    aria-label="Copy code"
                                    title="Copy"
                                  >
                                    📋
                                  </button>
                                  <code className={className} {...props}>
                                    {children}
                                  </code>
                                </div>
                              )
                            }
                            return (
                              <code className={className} {...props}>
                                {children}
                              </code>
                            )
                          }
                        }}
                      >
                        {maskSecrets(msg.content)}
                      </ReactMarkdown>
                    ) : (
                      <p>{maskSecrets(msg.content)}</p>
                    )}
                  </div>
                )}
                <span className="timestamp">{formatTime(msg.timestamp)}</span>
                {msg.quickReplies && msg.quickReplies.length > 0 && (
                  <div className="quick-replies" role="group" aria-label="Suggested responses">
                    {msg.quickReplies.map((reply, j) => (
                      <button
                        key={j}
                        className="quick-reply-btn"
                        onClick={() => handleQuickReply(reply)}
                        disabled={loading}
                      >
                        {reply}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
          {loading && (
            <div className="message assistant">
              <div className="avatar" aria-hidden="true">
                🤖
              </div>
              <div className="message-content">
                <div className="message-bubble typing" aria-label="Assistant is thinking">
                  <span className="dot" />
                  <span className="dot" />
                  <span className="dot" />
                  <span className="loading-step">{LOADING_STEPS[loadingStep]}</span>
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {showScrollBtn && (
          <button className="scroll-btn" onClick={scrollToBottom} aria-label="Scroll to latest message">
            ↓
          </button>
        )}
      </main>

      <footer className="input-area">
        <div className="input-container">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Describe your issue or paste logs here..."
            rows={1}
            disabled={loading}
            aria-label="Message input"
          />
          <button
            onClick={() => void sendMessage()}
            disabled={loading || !input.trim()}
            aria-label="Send message"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
            </svg>
          </button>
        </div>
        <p className="input-hint">
          Press Enter to send · Shift+Enter for new line · Chat restores after refresh
        </p>
      </footer>
    </div>
  )
}

export default App
