import { useState, useRef, useEffect, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import './App.css'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  quickReplies?: string[]
  action?: string
}

const INITIAL_MESSAGE: Message = {
  role: 'assistant',
  content: "Hey! I'm your RCA Agent 🔍\n\nI can help you investigate production issues by analyzing logs and git history. Tell me what's going wrong and I'll help figure out the root cause.\n\nYou can also upload log files or paste error logs directly.",
  timestamp: new Date().toISOString(),
  quickReplies: ['🔍 Investigate an issue', '📋 Paste logs', '📁 Upload log file']
}

function App() {
  const [messages, setMessages] = useState<Message[]>([INITIAL_MESSAGE])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [statusMessage, setStatusMessage] = useState<string | null>(null)
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [showScrollBtn, setShowScrollBtn] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const chatContainerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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

  // Auto-resize textarea
  useEffect(() => {
    const textarea = inputRef.current
    if (!textarea) return
    textarea.style.height = '42px'
    textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
  }, [input])

  const sendMessage = async (text?: string) => {
    const messageText = text || input.trim()
    if (!messageText || loading) return

    // Handle file upload trigger
    if (messageText === '📁 Upload log file') {
      fileInputRef.current?.click()
      return
    }

    const userMessage: Message = {
      role: 'user',
      content: messageText,
      timestamp: new Date().toISOString()
    }

    setMessages(prev => [
      ...prev.map(m => ({ ...m, quickReplies: undefined })),
      userMessage
    ])
    setInput('')
    setLoading(true)
    setStatusMessage('Processing your request...')

    try {
      // Use SSE streaming endpoint
      const response = await fetch('/api/v1/rca/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: messageText, sessionId })
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => null)
        const errorMsg = errorData?.error || 'Failed to get response'
        throw new Error(errorMsg)
      }

      // Parse SSE stream
      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      let resultReceived = false

      if (reader) {
        let buffer = ''
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              const eventName = line.slice(6).trim()
              if (eventName === 'status') {
                // Next data line is status
              } else if (eventName === 'error') {
                // Will be handled in data
              }
            } else if (line.startsWith('data:')) {
              const dataStr = line.slice(5).trim()
              try {
                const data = JSON.parse(dataStr)
                if (data.phase) {
                  // Status update
                  setStatusMessage(data.message || 'Analyzing...')
                } else if (data.message) {
                  // Final result
                  resultReceived = true
                  setSessionId(data.sessionId)
                  const assistantMessage: Message = {
                    role: 'assistant',
                    content: data.message,
                    timestamp: new Date().toISOString(),
                    quickReplies: data.quickReplies?.length > 0 ? data.quickReplies : undefined,
                    action: data.action
                  }
                  setMessages(prev => [...prev, assistantMessage])
                }
              } catch {
                // ignore parse errors for partial data
              }
            }
          }
        }
      }

      // Fallback: if SSE didn't work, try regular endpoint
      if (!resultReceived) {
        const fallbackResponse = await fetch('/api/v1/rca/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message: messageText, sessionId })
        })
        if (fallbackResponse.ok) {
          const data = await fallbackResponse.json()
          setSessionId(data.sessionId)
          setMessages(prev => [...prev, {
            role: 'assistant',
            content: data.message,
            timestamp: new Date().toISOString(),
            quickReplies: data.quickReplies?.length > 0 ? data.quickReplies : undefined,
            action: data.action
          }])
        }
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Something went wrong'
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: `⚠️ ${errorMessage}`,
        timestamp: new Date().toISOString(),
        quickReplies: ['🔄 Try again']
      }])
    } finally {
      setLoading(false)
      setStatusMessage(null)
      inputRef.current?.focus()
    }
  }

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setLoading(true)
    setStatusMessage('Uploading log file...')

    const userMessage: Message = {
      role: 'user',
      content: `📁 Uploading: ${file.name}`,
      timestamp: new Date().toISOString()
    }
    setMessages(prev => [
      ...prev.map(m => ({ ...m, quickReplies: undefined })),
      userMessage
    ])

    try {
      const formData = new FormData()
      formData.append('file', file)
      if (sessionId) formData.append('sessionId', sessionId)

      const response = await fetch('/api/v1/rca/chat/upload', {
        method: 'POST',
        body: formData
      })

      if (!response.ok) throw new Error('Upload failed')

      const data = await response.json()
      setSessionId(data.sessionId)
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: data.message,
        timestamp: new Date().toISOString(),
        quickReplies: data.quickReplies?.length > 0 ? data.quickReplies : undefined
      }])
    } catch {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: '❌ Failed to upload file. Please try pasting the log content directly.',
        timestamp: new Date().toISOString(),
        quickReplies: ['📋 Paste logs instead']
      }])
    } finally {
      setLoading(false)
      setStatusMessage(null)
      e.target.value = ''
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const newSession = () => {
    setMessages([INITIAL_MESSAGE])
    setSessionId(null)
    setInput('')
    setStatusMessage(null)
    inputRef.current?.focus()
  }

  const formatTime = (iso: string) => {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div className="app">
      <header className="header">
        <div className="header-content">
          <span className="logo" aria-hidden="true">🔍</span>
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
          {messages.map((msg, i) => (
            <div key={i} className={`message ${msg.role}`}>
              <div className="avatar" aria-hidden="true">
                {msg.role === 'assistant' ? '🤖' : '👤'}
              </div>
              <div className="message-content">
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
                                  onClick={() => navigator.clipboard.writeText(String(children))}
                                  aria-label="Copy code"
                                  title="Copy"
                                >
                                  📋
                                </button>
                                <code className={className} {...props}>{children}</code>
                              </div>
                            )
                          }
                          return <code className={className} {...props}>{children}</code>
                        }
                      }}
                    >
                      {msg.content}
                    </ReactMarkdown>
                  ) : (
                    <p>{msg.content}</p>
                  )}
                </div>
                <span className="timestamp">{formatTime(msg.timestamp)}</span>
                {msg.quickReplies && msg.quickReplies.length > 0 && (
                  <div className="quick-replies" role="group" aria-label="Suggested responses">
                    {msg.quickReplies.map((reply, j) => (
                      <button
                        key={j}
                        className="quick-reply-btn"
                        onClick={() => sendMessage(reply)}
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
              <div className="avatar" aria-hidden="true">🤖</div>
              <div className="message-content">
                <div className="message-bubble typing" aria-label="Assistant is thinking">
                  <span className="status-text">{statusMessage || 'Thinking...'}</span>
                  <span className="dot"></span>
                  <span className="dot"></span>
                  <span className="dot"></span>
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
          <button
            className="upload-btn"
            onClick={() => fileInputRef.current?.click()}
            disabled={loading}
            aria-label="Upload log file"
            title="Upload log file"
          >
            📎
          </button>
          <textarea
            ref={inputRef}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Describe your issue or paste logs here..."
            rows={1}
            disabled={loading}
            aria-label="Message input"
          />
          <button
            onClick={() => sendMessage()}
            disabled={loading || !input.trim()}
            aria-label="Send message"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
            </svg>
          </button>
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept=".log,.txt,.json,.csv"
          onChange={handleFileUpload}
          style={{ display: 'none' }}
        />
        <p className="input-hint">Press Enter to send · Shift+Enter for new line · 📎 to upload logs</p>
      </footer>
    </div>
  )
}

export default App
