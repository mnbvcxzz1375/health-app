export type AsyncViewState = 'idle' | 'loading' | 'success' | 'empty' | 'error'

export type RiskTone = 'default' | 'success' | 'warning' | 'danger' | 'info'

export type PageStatCard = {
  key: string
  label: string
  value: string
  hint?: string
  icon?: string
  tone?: RiskTone
}

export type StatusNoticeTone = 'loading' | 'empty' | 'error' | 'success' | 'info'
