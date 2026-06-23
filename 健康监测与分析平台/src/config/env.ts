/**
 * 环境变量读取
 */
const trueFlags = new Set(['1', 'true', 'yes', 'on'])

function parseBooleanFlag(value: string | boolean | undefined): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value !== 'string') return false
  return trueFlags.has(value.trim().toLowerCase())
}

export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
  postureApiBaseUrl: import.meta.env.VITE_POSTURE_API_BASE_URL ?? '/posture-api',
  appName: import.meta.env.VITE_APP_NAME ?? '健康监测与分析平台',
  useDevMock: parseBooleanFlag(import.meta.env.VITE_USE_DEV_MOCK ?? 'false'),
  llmApiBaseUrl: import.meta.env.VITE_LLM_API_BASE_URL ?? '',
  llmApiKey: import.meta.env.VITE_LLM_API_KEY ?? '',
  llmModel: import.meta.env.VITE_LLM_MODEL ?? 'mimo-v2.5',
}
