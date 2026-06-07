import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import './App.css'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  quickReplies?: string[]
}

function App() {
  const [messages, setMessages] = useState<Message[]>([
    {
      role: 'assistant',
      content: "Hey! I'm your RCA Agent 🔍\n\nI can help you investigate production issues by analyzing logs and git history. Tell me what's going wrong and I'll help figure out the root cause.\n\nYou can also ask me to generate an auto-fix PR once we identify the issue.",
      timestamp: new Date().toISOString(),
      quickReplies: ['🔍 Investigate an issue', '📋 Paste logs', '🔗 Analyze a repo']
    }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = async (text?: string) => {
    const messageText = text || input.trim()
    if (!messageText || loading) return

    const userMessage: Message = {
      role: 'user',
      content: messageText,
      timestamp: new Date().toISOString()
    }

    // Clear quick replies from previous messages
    setMessages(prev => [
      ...prev.map(m => ({ ...m, quickReplies: undefined })),
      userMessage
    ])
    setInput('')
    setLoading(true)

    try {
      const response = await fetch('/api/v1/rca/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: messageText, sessionId })
      })

      if (!response.ok) throw new Error('Failed to get response')

      const data = await response.json()
      setSessionId(data.sessionId)

      const assistantMessage: Message = {
        role: 'assistant',
        content: data.message,
        timestamp: new Date().toISOString(),
        quickReplies: data.quickReplies?.length > 0 ? data.quickReplies : undefined
      }
      setMessages(prev => [...prev, assistantMessage])
    } catch (err) {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: '❌ Something went wrong. Please try again.',
        timestamp: new Date().toISOString(),
        quickReplies: ['🔄 Try again']
      }])
    } finally {
      setLoading(false)
      inputRef.current?.focus()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const handleQuickReply = (text: string) => {
    sendMessage(text)
  }

  return (
    <div className="app">
      <header className="header">
        <div className="header-content">
          <span className="logo">🔍</span>
          <h1>RCA Agent</h1>
          <span className="subtitle">Root Cause Analysis Assistant</span>
        </div>
      </header>

      <main className="chat-container">
        <div className="messages">
          {messages.map((msg, i) => (
            <div key={i} className={`message ${msg.role}`}>
              <div className="message-bubble">
                {msg.role === 'assistant' ? (
                  <ReactMarkdown>{msg.content}</ReactMarkdown>
                ) : (
                  <p>{msg.content}</p>
                )}
              </div>
              {msg.quickReplies && msg.quickReplies.length > 0 && (
                <div className="quick-replies">
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
          ))}
          {loading && (
            <div className="message assistant">
              <div className="message-bubble typing">
                <span className="dot"></span>
                <span className="dot"></span>
                <span className="dot"></span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>
      </main>

      <footer className="input-area">
        <div className="input-container">
          <textarea
            ref={inputRef}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Describe your issue or paste logs here..."
            rows={1}
            disabled={loading}
          />
          <button onClick={() => sendMessage()} disabled={loading || !input.trim()}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
            </svg>
          </button>
        </div>
      </footer>
    </div>
  )
}

export default App
