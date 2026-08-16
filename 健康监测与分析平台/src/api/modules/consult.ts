import { http } from '@/api/http'
import { env } from '@/config/env'

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
  evidence: ConsultEvidence[]
  safety: ConsultSafety
}

export type ConsultEvidence = {
  id: string
  title: string
  sourceType: string
  field: string
  excerpt: string
  retrievalScore: number | null
}

export type ConsultSafety = {
  level: 'routine' | 'emergency' | string
  flags: string[]
  uncertainty: string
  escalation: string
  actionTags: string[]
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
  evidence: ConsultEvidence[]
  safety: ConsultSafety | null
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
  if (env.useDevMock) {
    return {
      requestId: 'mock-req-' + Date.now(),
      answer: `这是一个模拟回复。您的问题：${payload.question}\n\n根据您的健康数据，建议您保持规律的运动和充足的睡眠。如有具体不适，请及时就医。`,
      suggestions: ['如何改善睡眠质量？', '推荐适合的运动方式', '日常饮食注意事项'],
      disclaimer: '此为模拟回复，仅供参考，不构成医疗建议。',
      evidence: [{
        id: 'mock-health-context',
        title: '模拟健康管理上下文',
        sourceType: 'demo',
        field: '',
        excerpt: '开发模式下不使用真实知识库，正式展示请切换至后端可追溯链路。',
        retrievalScore: null,
      }],
      safety: {
        level: 'routine',
        flags: ['DEMO_MODE'],
        uncertainty: '当前为开发模拟结果。',
        escalation: '如有不适或用药疑问，请联系医生或药师。',
        actionTags: ['REASSESS_BEFORE_PROGRESSION'],
      },
    }
  }
  const { data } = await http.post<ConsultResponse>('/consult/questions', payload, { timeout: 90_000 })
  return data
}

/*
 * Legacy direct-browser LLM implementation intentionally disabled.
 * Consult requests must use the authenticated backend path so that PII scrubbing,
 * knowledge retrieval, safety assessment, and audit persistence cannot be bypassed.
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
      evidence: [],
      safety: defaultSafety(),
    }
  } catch {
    return {
      requestId: 'llm-fallback-' + Date.now(),
      answer: 'The system will generate specific recommendations based on your latest monitoring report. Please complete health data sync first.',
      suggestions: ['Complete Apple Health sync', 'Upload recent health report', 'View health score trends'],
      disclaimer: 'This content is for health management assistance only.',
      evidence: [],
      safety: defaultSafety(),
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

*/

/**
 * SSE streaming via GET endpoint. Yields text chunks as they arrive.
 * The final "done" event carries metadata (requestId, suggestions, disclaimer).
 */
export async function* streamConsultSSE(
  question: string,
  scene = 'assistant',
): AsyncGenerator<{ chunk?: string; done?: Omit<ConsultResponse, 'answer'> }> {
  const token = readToken()
  const headers: Record<string, string> = {}
  if (token) headers['Authorization'] = 'Bearer ' + token

  const response = await fetch(
    `/api/consult/stream?question=${encodeURIComponent(question)}&scene=${scene}`,
    { headers },
  )
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  if (!response.body) throw new Error('No response body')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) continue
      if (trimmed.startsWith('event:done')) continue
      if (trimmed.startsWith('data:')) {
        const payload = trimmed.slice(5).trim()
        // Check if the next line in the buffer is the done event
        // The done event's data line contains JSON metadata
        try {
          const meta = JSON.parse(payload)
          if (meta.requestId) {
            yield {
              done: {
                requestId: meta.requestId,
                suggestions: meta.suggestions ?? [],
                disclaimer: meta.disclaimer ?? '',
                evidence: meta.evidence ?? [],
                safety: meta.safety ?? defaultSafety(),
              },
            }
            continue
          }
        } catch {
          // not JSON, it's a text chunk
        }
        yield { chunk: payload }
      }
    }
  }
}

export async function streamConsultQuestion(
  payload: ConsultQuestionPayload,
  handlers: { onChunk?: (delta: string) => void; onComplete?: (response: ConsultResponse) => void },
): Promise<void> {
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
        evidence: event.evidence ?? [], safety: event.safety ?? defaultSafety(),
      })
      if (event.type === 'error') throw new Error(event.message)
    }
  }
}

export async function getConsultHistory(limit = 20, offset = 0): Promise<ConsultHistoryItem[]> {
  if (env.useDevMock) return []
  try {
    const { data } = await http.get(`/consult/history?limit=${limit}&offset=${offset}`)
    return (data as Record<string, unknown>[]).map(item => ({
      id: item.id as number,
      requestId: item.request_id as string,
      scene: item.scene as string,
      question: item.question as string,
      answer: item.answer as string,
      suggestions: parseJsonArray<string>(item.suggestions_json),
      disclaimer: (item.disclaimer as string) ?? '',
      knowledgeSources: parseJsonArray<string>(item.knowledge_sources_json),
      evidence: parseJsonArray<ConsultEvidence>(item.evidence_json),
      safety: parseSafety(item.safety_json),
      modelUsed: (item.model_used as string) ?? '',
      createdAt: item.created_at as string,
    }))
  } catch { return [] }
}

export async function deleteConsultHistory(id: number): Promise<void> {
  if (env.useDevMock) return
  await http.delete(`/consult/history/${id}`)
}

export async function clearConsultHistory(): Promise<void> {
  if (env.useDevMock) return
  await http.delete('/consult/history')
}

function parseJsonArray<T>(value: unknown): T[] {
  if (typeof value !== 'string') return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed as T[] : []
  } catch {
    return []
  }
}

function parseSafety(value: unknown): ConsultSafety | null {
  if (typeof value !== 'string') return null
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object') return null
    const safety = parsed as Partial<ConsultSafety>
    return {
      level: safety.level ?? 'routine',
      flags: Array.isArray(safety.flags) ? safety.flags : [],
      uncertainty: safety.uncertainty ?? '安全元数据暂不可用。',
      escalation: safety.escalation ?? '如有不适或用药疑问，请联系医生或药师。',
      actionTags: Array.isArray(safety.actionTags) ? safety.actionTags : [],
    }
  } catch {
    return null
  }
}

function defaultSafety(): ConsultSafety {
  return {
    level: 'routine',
    flags: ['SAFETY_METADATA_UNAVAILABLE'],
    uncertainty: '安全元数据暂不可用。',
    escalation: '如有不适或用药疑问，请联系医生或药师。',
    actionTags: ['NO_PERSONALIZED_GUIDANCE', 'REQUEST_MORE_EVIDENCE'],
  }
}
