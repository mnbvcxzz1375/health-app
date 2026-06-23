import { onBeforeUnmount, ref, watch } from 'vue'
import { getMedicationAlarms, type MedicationAlarm } from '@/api/modules/medication'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import {
  formatMedicationAlarmDateKey,
  MEDICATION_ALARM_CHANGED_EVENT,
} from '@/modules/medication/utils/medicationAlarm'

let overlayHost: HTMLDivElement | null = null
let overlayTimer: number | null = null

const ensureOverlayHost = () => {
  if (typeof document === 'undefined') return null
  if (overlayHost && document.body.contains(overlayHost)) return overlayHost
  overlayHost = document.createElement('div')
  overlayHost.setAttribute('data-medication-alarm-overlay', 'true')
  document.body.appendChild(overlayHost)
  return overlayHost
}

const closeOverlay = () => {
  if (overlayTimer) {
    window.clearTimeout(overlayTimer)
    overlayTimer = null
  }
  if (overlayHost) {
    overlayHost.remove()
    overlayHost = null
  }
}

const showOverlay = (alarm: MedicationAlarm) => {
  const host = ensureOverlayHost()
  if (!host) return

  const names = alarm.medications.map((item) => item.alias || item.name).filter(Boolean).join('、')
  const subtitle = names ? `请立即服用：${names}` : '请立即查看本次用药提醒'

  host.innerHTML = `
    <div style="position:fixed;inset:0;z-index:9999;pointer-events:none;">
      <div style="position:absolute;inset:0;background:rgba(15,23,42,.18);backdrop-filter:blur(4px);"></div>
      <div style="position:absolute;left:50%;top:32px;transform:translateX(-50%);width:min(92vw,460px);pointer-events:auto;">
        <div style="border-radius:28px;padding:20px 20px 18px;background:linear-gradient(180deg,#fff7ed 0%,#ffffff 100%);border:2px solid rgba(234,88,12,.24);box-shadow:0 24px 60px rgba(15,23,42,.24);animation:med-alarm-pop .35s ease-out;">
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:16px;">
            <div style="min-width:0;">
              <div style="display:inline-flex;align-items:center;gap:8px;border-radius:999px;background:rgba(234,88,12,.12);color:#9a3412;padding:6px 12px;font-size:12px;font-weight:700;">到点服药提醒</div>
              <div style="margin-top:14px;font-size:30px;line-height:1.1;font-weight:800;color:#111827;">${alarm.time}</div>
              <div style="margin-top:8px;font-size:16px;line-height:1.7;color:#7c2d12;font-weight:600;">${subtitle}</div>
            </div>
            <button type="button" data-medication-overlay-close="true" style="border:none;background:#fff;border-radius:999px;width:40px;height:40px;font-size:20px;line-height:1;color:#9a3412;box-shadow:0 10px 24px rgba(15,23,42,.12);cursor:pointer;">×</button>
          </div>
          <div style="margin-top:14px;padding:12px 14px;border-radius:18px;background:#fff;border:1px solid rgba(234,88,12,.16);color:#4b5563;font-size:13px;line-height:1.7;">
            用药提醒已触发，请确认已按时服用。关闭页面前也会继续显示系统通知。
          </div>
        </div>
      </div>
    </div>
    <style>
      @keyframes med-alarm-pop {
        0% { transform: translateY(-18px) scale(.96); opacity: 0; }
        100% { transform: translateY(0) scale(1); opacity: 1; }
      }
    </style>
  `

  const closeButton = host.querySelector('[data-medication-overlay-close="true"]')
  closeButton?.addEventListener('click', closeOverlay, { once: true })
  overlayTimer = window.setTimeout(closeOverlay, 18000)
}

export function useMedicationAlarmNotifications() {
  const { warning } = useToast()
  const authStore = useAuthStore()
  const alarms = ref<MedicationAlarm[]>([])
  const triggered = new Set<string>()
  let checkTimer: number | null = null
  let refreshTimer: number | null = null

  const requestNotificationPermission = async () => {
    if (typeof window === 'undefined' || !('Notification' in window)) return
    if (window.Notification.permission !== 'default') return
    try {
      await window.Notification.requestPermission()
    } catch {
      // ignore
    }
  }

  const refreshAlarms = async () => {
    if (!authStore.isAuthenticated) {
      alarms.value = []
      return
    }
    try {
      alarms.value = await getMedicationAlarms()
    } catch (error) {
      warning('用药提醒同步失败', error instanceof Error ? error.message : '稍后会自动重试。', 2500)
    }
  }

  const refreshAndCheck = async () => {
    await refreshAlarms()
    runScheduleCheck()
  }

  const notifyAlarm = (alarm: MedicationAlarm) => {
    const names = alarm.medications.map((item) => item.alias || item.name).filter(Boolean).join('、')
    const title = '到点服药提醒'
    const description = names ? `请立即服用：${names}` : '请立即查看本次用药提醒'

    warning(title, description, 18000)
    showOverlay(alarm)

    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate?.([280, 140, 280, 140, 280, 140, 560])
      } catch {
        // ignore
      }
    }

    if (typeof window !== 'undefined' && 'Notification' in window && window.Notification.permission === 'granted') {
      try {
        const notification = new window.Notification(title, {
          body: description,
          tag: `medication-alarm-${alarm.id}-${alarm.time}`,
          requireInteraction: true,
        })
        window.setTimeout(() => notification.close(), 16000)
      } catch {
        // ignore
      }
    }
  }

  const runScheduleCheck = () => {
    const now = new Date()
    const today = formatMedicationAlarmDateKey(now)
    const currentTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`

    alarms.value.forEach((alarm) => {
      const key = `${today}_${alarm.id}_${alarm.time}`
      if (!alarm.enabled || alarm.time !== currentTime || triggered.has(key)) return
      notifyAlarm(alarm)
      triggered.add(key)
    })

    for (const key of Array.from(triggered)) {
      if (!key.startsWith(today)) {
        triggered.delete(key)
      }
    }
  }

  const handleWindowFocus = () => {
    void refreshAndCheck()
  }

  const handleAlarmChanged = () => {
    void refreshAndCheck()
  }

  const startPolling = () => {
    stopPolling()
    checkTimer = window.setInterval(runScheduleCheck, 30_000)
    refreshTimer = window.setInterval(() => {
      void refreshAlarms()
    }, 60_000)
    window.addEventListener('focus', handleWindowFocus)
    window.addEventListener(MEDICATION_ALARM_CHANGED_EVENT, handleAlarmChanged)
  }

  const stopPolling = () => {
    if (checkTimer) {
      window.clearInterval(checkTimer)
      checkTimer = null
    }
    if (refreshTimer) {
      window.clearInterval(refreshTimer)
      refreshTimer = null
    }
    window.removeEventListener('focus', handleWindowFocus)
    window.removeEventListener(MEDICATION_ALARM_CHANGED_EVENT, handleAlarmChanged)
    alarms.value = []
  }

  watch(
    () => authStore.isAuthenticated,
    async (authenticated) => {
      if (authenticated) {
        await requestNotificationPermission()
        await refreshAlarms()
        runScheduleCheck()
        startPolling()
      } else {
        stopPolling()
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    stopPolling()
    closeOverlay()
  })
}
