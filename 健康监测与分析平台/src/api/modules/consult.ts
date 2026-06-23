import { http } from '@/api/http'
import { env } from '@/config/env'
import { getContextSnapshot } from '@/api/modules/context'

export type ConsultScene = 'home_overview' | 'assistant'

export type ConsultQuestionPayload = {
  question: string
  scene?: ConsultScene
}

export type ConsultResponse = {
  requestId: string
  answer: string
  suggestions: string[]
  disclaimer: string
}

export type ConsultHistoryItem = {
  id: number
  requestId: string
  scene: string
  question: string
  answer: string
  suggestions: string[]
  disclaimer: string
  knowledgeSources: string[]
  modelUsed: string
  createdAt: string
}

const SESSION_KEY = 'hm_auth_session'

function readToken() {
  if (typeof window === 'undefined') return ''
  const raw = window.localStorage.getItem(SESSION_KEY)
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw) as { token?: string }
    return parsed?.token ?? ''
  } catch {
    return ''
  }
}

export async function askConsultQuestion(payload: ConsultQuestionPayload): Promise<ConsultResponse> {
  if (env.llmApiKey) {
    return callLLMDirectAPI(payload)
  }
  const { data } = await http.post<ConsultResponse>('/consult/questions', payload, { timeout: 90_000 })
  return data
}

async function callLLMDirectAPI(payload: ConsultQuestionPayload): Promise<ConsultResponse> {
  let contextBlock = ''
  try {
    const ctx = await getContextSnapshot()
    const parts = []
    if (ctx.systemSummary) parts.push('user profile: ' + ctx.systemSummary)
    if (ctx.dailySummary) parts.push('today metrics: ' + ctx.dailySummary)
    if (ctx.activeConcerns.length) parts.push('active concerns: ' + ctx.activeConcerns.join(', '))
    if (ctx.currentMedications.length) parts.push('medications: ' + ctx.currentMedications.join(', '))
    if (parts.length) contextBlock = '\n\nUser context:\n' + parts.join('\n')
  } catch {
    // context unavailable, proceed without it
  }

  const systemPrompt = payload.scene === 'assistant'
    ? 'You are a Chinese health management assistant. Give concise, actionable advice based on the user health data. Keep each answer under 150 Chinese characters. Always end with a safety disclaimer that this is not medical advice.'
    : 'You are a Chinese health management assistant. Based on the user health score and recent monitoring data, give 1-2 actionable improvement suggestions for tonight and tomorrow. Keep under 120 Chinese characters.'

  const body = JSON.stringify({
    model: env.llmModel || 'mimo-v2.5',
    messages: [
      { role: 'system', content: systemPrompt + contextBlock },
      { role: 'user', content: payload.question },
    ],
    temperature: 0.6,
    max_tokens: 400,
  })

  try {
    const response = await fetch(env.llmApiBaseUrl + '/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + env.llmApiKey,
      },
      body,
    })

    if (!response.ok) throw new Error('LLM API status ' + response.status)

    const json = await response.json()
    const answer = json?.choices?.[0]?.message?.content || 'temporarily unavailable'

    return {
      requestId: 'llm-' + Date.now(),
      answer,
      suggestions: extractSuggestions(answer),
      disclaimer: 'This content is for health management assistance only and does not replace medical diagnosis or treatment.',
    }
  } catch {
    return {
      requestId: 'llm-fallback-' + Date.now(),
      answer: 'The system will generate specific recommendations based on your latest monitoring report. Please complete health data sync first.',
      suggestions: ['Complete Apple Health sync', 'Upload recent health report', 'View health score trends'],
      disclaimer: 'This content is for health management assistance only.',
    }
  }
}

function extractSuggestions(text: string): string[] {
  // Try to parse structured JSON response from LLM
  try {
    const jsonMatch = text.match(/\{[\s\S]*\}/)
    if (jsonMatch) {
      const parsed = JSON.parse(jsonMatch[0])
      if (Array.isArray(parsed.suggestions) && parsed.suggestions.length > 0) {
        return parsed.suggestions.filter((s: unknown) => typeof s === 'string' && s.length > 2).slice(0, 3)
      }
    }
  } catch {
    // Not JSON, use defaults
  }
  // Provide contextual default suggestions instead of parsing the answer text
  return [
    '帮我分析一下今天的睡眠质量',
    '根据数据给我一份恢复建议',
    '生成明天的健康行动计划',
  ]
}

export async function streamConsultQuestion(
  payload: ConsultQuestionPayload,
  handlers: { onChunk?: (delta: string) => void; onComplete?: (response: ConsultResponse) => void },
): Promise<void> {
  if (env.llmApiKey) {
    const result = await callLLMDirectAPI(payload)
    for (let i = 0; i < result.answer.length; i += 3) {
      handlers.onChunk?.(result.answer.slice(i, i + 3))
      await new Promise(r => setTimeout(r, 15))
    }
    handlers.onComplete?.(result)
    return
  }

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = readToken()
  if (token) headers.Authorization = 'Bearer ' + token

  const response = await fetch(env.apiBaseUrl + '/consult/stream', {
    method: 'POST', headers, body: JSON.stringify(payload),
  })

  if (!response.ok || !response.body) throw new Error('stream error')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) continue
      const event = JSON.parse(trimmed)
      if (event.type === 'chunk') handlers.onChunk?.(event.delta)
      if (event.type === 'complete') handlers.onComplete?.({
        requestId: event.requestId, answer: event.answer,
        suggestions: event.suggestions, disclaimer: event.disclaimer,
      })
      if (event.type === 'error') throw new Error(event.message)
    }
  }
}

export async function getConsultHistory(limit = 20, offset = 0): Promise<ConsultHistoryItem[]> {
  try {
    const { data } = await http.get(`/consult/history?limit=${limit}&offset=${offset}`)
    return (data as Record<string, unknown>[]).map(item => ({
      id: item.id as number,
      requestId: item.request_id as string,
      scene: item.scene as string,
      question: item.question as string,
      answer: item.answer as string,
      suggestions: typeof item.suggestions_json === 'string' ? JSON.parse(item.suggestions_json as string) : [],
      disclaimer: (item.disclaimer as string) ?? '',
      knowledgeSources: typeof item.knowledge_sources_json === 'string' ? JSON.parse(item.knowledge_sources_json as string) : [],
      modelUsed: (item.model_used as string) ?? '',
      createdAt: item.created_at as string,
    }))
  } catch { return [] }
}

export async function deleteConsultHistory(id: number): Promise<void> {
  await http.delete(`/consult/history/${id}`)
}

export async function clearConsultHistory(): Promise<void> {
  await http.delete('/consult/history')
}
