import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
    text: async () => (ok ? JSON.stringify(body) : 'upstream error')
  } as Response
}

describe('App chat UI', () => {
  beforeEach(() => {
    HTMLElement.prototype.scrollIntoView = vi.fn()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          message: 'Looking into the NPE',
          sessionId: 'sess-1',
          quickReplies: ['Share more logs']
        })
      )
    )
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders initial quick replies', () => {
    render(<App />)
    expect(screen.getByRole('button', { name: '🔍 Investigate an issue' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '📋 Paste logs' })).toBeInTheDocument()
  })

  it('sendMessage posts the chat body to /api/v1/rca/chat', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Message input'), 'NullPointerException on login')
    await user.click(screen.getByLabelText('Send message'))

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/rca/chat',
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: 'NullPointerException on login',
            sessionId: null
          })
        })
      )
    })

    expect(await screen.findByText('Looking into the NPE')).toBeInTheDocument()
  })

  it('quick-reply click sends that reply text', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: '🔍 Investigate an issue' }))

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/rca/chat',
        expect.objectContaining({
          body: JSON.stringify({
            message: '🔍 Investigate an issue',
            sessionId: null
          })
        })
      )
    })
  })

  it('renders a status-aware fallback when fetch fails', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({}, false, 503))
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Message input'), 'still broken')
    await user.click(screen.getByLabelText('Send message'))

    expect(await screen.findByText(/Request failed \(503\)/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '🔄 Try again' })).toBeInTheDocument()
  })

  it('Try again resends without stacking duplicate user bubbles', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock
      .mockResolvedValueOnce(jsonResponse({}, false, 500))
      .mockResolvedValueOnce(
        jsonResponse({
          message: 'Recovered',
          sessionId: 'sess-2',
          quickReplies: []
        })
      )

    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Message input'), 'retry me')
    await user.click(screen.getByLabelText('Send message'))
    expect(await screen.findByText(/Request failed \(500\)/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '🔄 Try again' }))
    expect(await screen.findByText('Recovered')).toBeInTheDocument()

    const userBubbles = screen.getAllByText('retry me')
    expect(userBubbles).toHaveLength(1)
  })

  it('masks pasted GitHub tokens in the user bubble', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Message input'), 'ghp_secretTokenValue')
    await user.click(screen.getByLabelText('Send message'))

    expect(await screen.findByText('Looking into the NPE')).toBeInTheDocument()
    expect(screen.queryByText('ghp_secretTokenValue')).not.toBeInTheDocument()
    expect(screen.getByText(/ghp_••••••••••••/)).toBeInTheDocument()
  })

  it('newSession restores the welcome prompt and clears server session', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText('Message input'), 'hello')
    await user.click(screen.getByLabelText('Send message'))
    expect(await screen.findByText('Looking into the NPE')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Start new conversation'))

    expect(screen.queryByText('Looking into the NPE')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '🔍 Investigate an issue' })).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/rca/chat/sess-1', { method: 'DELETE' })
  })
})
