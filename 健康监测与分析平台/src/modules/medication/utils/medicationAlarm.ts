export const MEDICATION_ALARM_CHANGED_EVENT = 'medication-alarm:changed'

export function emitMedicationAlarmChangedEvent() {
  if (typeof window === 'undefined') return

  const event =
    typeof window.CustomEvent === 'function'
      ? new window.CustomEvent(MEDICATION_ALARM_CHANGED_EVENT)
      : new Event(MEDICATION_ALARM_CHANGED_EVENT)

  window.dispatchEvent(event)
}

export function formatMedicationAlarmDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
