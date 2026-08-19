import { describe, expect, it } from 'vitest'
import { formatTime } from './formatTime'

describe('formatTime', () => {
  it('returns a clock string that contains a digit', () => {
    const out = formatTime('2026-08-19T12:00:00.000Z')
    expect(out.length).toBeGreaterThan(0)
    expect(out).toMatch(/\d/)
  })
})
