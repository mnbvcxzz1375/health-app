import { onBeforeUnmount, ref, watch } from 'vue'
import { getRehabPlanReminder, type PlanReminderDraft } from '@/api/modules/rehab'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'

let overlayHost: HTMLDivElement | null = null
let overlayTimer: number | null = null

const dayKeys = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'] as const
type ReminderDayKey = (typeof dayKeys)[number]

const ensureOverlayHost = () => {
  if (typeof document === 'undefined') return null
  if (overlayHost && document.body.contains(overlayHost)) return overlayHost
  overlayHost = document.createElement('div')
  overlayHost.setAttribute('data-rehab-plan-overlay', 'true')
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

const showOverlay = (reminder: PlanReminderDraft) => {
  const host = ensureOverlayHost()
  if (!host) return

  host.innerHTML = `
    <div style="position:fixed;inset:0;z-index:9998;pointer-events:none;">
      <div style="position:absolute;inset:0;background:rgba(15,23,42,.16);backdrop-filter:blur(4px);"></div>
      <div style="position:absolute;left:50%;top:32px;transform:translateX(-50%);width:min(92vw,460px);pointer-events:auto;">
        <div style="border-radius:28px;padding:20px 20px 18px;background:linear-gradient(180deg,#ecfeff 0%,#ffffff 100%);border:2px solid rgba(13,148,136,.22);box-shadow:0 24px 60px rgba(15,23,42,.24);animation:rehab-alarm-pop .35s ease-out;">
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:16px;">
            <div style="min-width:0;">
              <div style="display:inline-flex;align-items:center;gap:8px;border-radius:999px;background:rgba(13,148,136,.12);color:#115e59;padding:6px 12px;font-size:12px;font-weight:700;">康复计划提醒</div>
              <div style="margin-top:14px;font-size:30px;line-height:1.1;font-weight:800;color:#0f172a;">${reminder.time}</div>
              <div style="margin-top:8px;font-size:16px;line-height:1.7;color:#0f766e;font-weight:600;">请开始今天的康复训练，并及时完成动作清单。</div>
            </div>
            <button type="button" data-rehab-overlay-close="true" style="border:none;background:#fff;border-radius:999px;width:40px;height:40px;font-size:20px;line-height:1;color:#115e59;box-shadow:0 10px 24px rgba(15,23,42,.12);cursor:pointer;">×</button>
          </div>
          <div style="margin-top:14px;padding:12px 14px;border-radius:18px;background:#fff;border:1px solid rgba(13,148,136,.16);color:#4b5563;font-size:13px;line-height:1.7;">
            本提醒与用药提醒一致，会同时触发站内浮层、系统通知和震动提示。
          </div>
        </div>
      </div>
    </div>
    <style>
      @keyframes rehab-alarm-pop {
        0% { transform: translateY(-18px) scale(.96); opacity: 0; }
        100% { transform: translateY(0) scale(1); opacity: 1; }
      }
    </style>
  `

  host.querySelector('[data-rehab-overlay-close="true"]')?.addEventListener('click', closeOverlay, { once: true })
  overlayTimer = window.setTimeout(closeOverlay, 18000)
}

const isReminderDueToday = (days: string[]) => {
  const currentDay = dayKeys[new Date().getDay()]
  return days.includes(currentDay)
}

export function useRehabPlanNotifications() {
  const { warning } = useToast()
  const authStore = useAuthStore()
  const reminder = ref<PlanReminderDraft | null>(null)
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

  const refreshReminder = async () => {
    if (!authStore.isAuthenticated) {
      reminder.value = null
      return
    }
    try {
      reminder.value = await getRehabPlanReminder()
    } catch (error) {
      warning('康复提醒同步失败', error instanceof Error ? error.message : '稍后会自动重试。', 2500)
    }
  }

  const notifyReminder = (current: PlanReminderDraft) => {
    const title = '康复计划提醒'
    const description = '请开始今天的康复训练，并查看当前动作清单。'

    warning(title, description, 18000)
    showOverlay(current)

    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate?.([260, 120, 260, 120, 520])
      } catch {
        // ignore
      }
    }

    if (typeof window !== 'undefined' && 'Notification' in window && window.Notification.permission === 'granted') {
      try {
        const notification = new window.Notification(title, {
          body: `${current.time} · ${description}`,
          tag: `rehab-plan-reminder-${current.time}`,
          requireInteraction: true,
        })
        window.setTimeout(() => notification.close(), 16000)
      } catch {
        // ignore
      }
    }
  }

  const runScheduleCheck = () => {
    const current = reminder.value
    if (!current?.pushEnabled || !current.time || !Array.isArray(current.days) || !current.days.length) return
    if (!isReminderDueToday(current.days as ReminderDayKey[])) return

    const now = new Date()
    const today = now.toISOString().slice(0, 10)
    const currentTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    const key = `${today}_${current.time}`

    if (current.time !== currentTime || triggered.has(key)) return
    notifyReminder(current)
    triggered.add(key)

    for (const item of Array.from(triggered)) {
      if (!item.startsWith(today)) {
        triggered.delete(item)
      }
    }
  }

  const handleWindowFocus = () => {
    void refreshReminder()
    runScheduleCheck()
  }

  const startPolling = () => {
    stopPolling()
    checkTimer = window.setInterval(runScheduleCheck, 30_000)
    refreshTimer = window.setInterval(() => {
      void refreshReminder()
    }, 60_000)
    window.addEventListener('focus', handleWindowFocus)
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
    reminder.value = null
  }

  watch(
    () => authStore.isAuthenticated,
    async (authenticated) => {
      if (authenticated) {
        await requestNotificationPermission()
        await refreshReminder()
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
