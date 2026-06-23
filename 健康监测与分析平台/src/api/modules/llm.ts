/**
 * Frontend LLM service - calls LLM API directly in mock mode
 * Backend proxy path: /api/consult/questions
 */
import { env } from '@/config/env'

export type LLMResponse = {
  requestId: string
  answer: string
  suggestions: string[]
  disclaimer: string
}

type LLMStreamChunk = {
  type: 'chunk'
  delta: string
}

type LLMComplete = {
  type: 'complete'
  requestId: string
  answer: string
  suggestions: string[]
  disclaimer: string
}

type LLMStreamEvent = LLMStreamChunk | LLMComplete | { type: 'error'; message: string }

export async function callLLM(payload: {
  question: string
  scene?: string
}): Promise<LLMResponse> {
  if (env.useDevMock && env.llmApiKey) {
    return callLLMDirect(payload)
  }

  const { http } = await import('@/api/http')
  const { data } = await http.post<LLMResponse>('/consult/questions', payload, {
    timeout: 90_000,
  })
  return data
}

async function callLLMDirect(payload: {
  question: string
  scene?: string
}): Promise<LLMResponse> {
  const body = JSON.stringify({
    model: env.llmModel || 'mimo-v2.5',
    messages: [
      {
        role: 'system',
        content: '你是健康管理助手，根据用户健康数据给出简洁有用的建议。每次回答控制在150字以内，给出2-3条可执行的行动建议。',
      },
      { role: 'user', content: payload.question },
    ],
    temperature: 0.6,
    max_tokens: 512,
  })

  const response = await fetch(env.llmApiBaseUrl + '/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + env.llmApiKey,
    },
    body,
  })

  if (!response.ok) {
    throw new Error('LLM API error: ' + response.status)
  }

  const json = await response.json()
  const answer = json?.choices?.[0]?.message?.content || ''

  return {
    requestId: 'llm-' + Date.now(),
    answer: answer,
    suggestions: extractSuggestions(answer),
    disclaimer: '以上内容仅用于健康管理辅助，不替代医生诊断与治疗。',
  }
}

function extractSuggestions(text: string): string[] {
  const lines = text.split(/[\n\r]+/).filter(Boolean)
  const suggestions: string[] = []
  for (const line of lines) {
    const cleaned = line.replace(/^[0-9]+[.、)]?\s*/, '').trim()
    if (cleaned.length > 4 && cleaned.length < 80) {
      suggestions.push(cleaned)
    }
  }
  return suggestions.slice(0, 4)
}

export async function streamLLM(
  payload: { question: string; scene?: string },
  handlers: {
    onChunk?: (delta: string) => void
    onComplete?: (response: LLMResponse) => void
  },
): Promise<void> {
  if (env.useDevMock && env.llmApiKey) {
    // Direct call, simulate streaming by returning full response
    const result = await callLLMDirect(payload)
    for (let i = 0; i < result.answer.length; i += 3) {
      handlers.onChunk?.(result.answer.slice(i, i + 3))
      await new Promise(r => setTimeout(r, 15))
    }
    handlers.onComplete?.(result)
    return
  }

  // Backend streaming
  const { env: envModule } = await import('@/config/env')
  const token = typeof window !== 'undefined'
    ? (() => { try { const raw = window.localStorage.getItem('hm_auth_session'); return raw ? JSON.parse(raw).token : '' } catch { return '' } })()
    : ''

  const response = await fetch(envModule.apiBaseUrl + '/consult/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok || !response.body) {
    throw new Error('LLM stream error')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) continue
      const event = JSON.parse(trimmed) as LLMStreamEvent
      if (event.type === 'chunk') handlers.onChunk?.(event.delta)
      if (event.type === 'complete') handlers.onComplete?.({
        requestId: event.requestId,
        answer: event.answer,
        suggestions: event.suggestions,
        disclaimer: event.disclaimer,
      })
      if (event.type === 'error') throw new Error(event.message)
    }
  }
}
