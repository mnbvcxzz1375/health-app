import { render } from '@testing-library/vue'
import { flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { MedicationAlarm } from '@/api/modules/medication'
import {
  formatMedicationAlarmDateKey,
  MEDICATION_ALARM_CHANGED_EVENT,
} from '@/modules/medication/utils/medicationAlarm'
import { useMedicationAlarmNotifications } from './useMedicationAlarmNotifications'

const { mockGetMedicationAlarms, mockWarning } = vi.hoisted(() => ({
  mockGetMedicationAlarms: vi.fn<() => Promise<MedicationAlarm[]>>(),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/modules/medication', () => ({
  getMedicationAlarms: mockGetMedicationAlarms,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: vi.fn(),
    warning: mockWarning,
    info: vi.fn(),
    error: vi.fn(),
  }),
}))

const mockIsAuthenticated = { value: true }

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return mockIsAuthenticated.value
    },
  }),
}))

const TestHarness = defineComponent({
  setup() {
    useMedicationAlarmNotifications()
    return () => h('div')
  },
})

describe('useMedicationAlarmNotifications', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 2, 20, 8, 0, 0))
    mockGetMedicationAlarms.mockReset()
    mockWarning.mockReset()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('formats day keys from local date parts instead of UTC serialization', () => {
    const fakeDate = {
      getFullYear: () => 2026,
      getMonth: () => 2,
      getDate: () => 20,
      toISOString: () => '2026-03-19T16:00:00.000Z',
    } as unknown as Date

    expect(formatMedicationAlarmDateKey(fakeDate)).toBe('2026-03-20')
  })

  it('refreshes alarms immediately on synchronization events and only notifies once per day', async () => {
    mockGetMedicationAlarms
      .mockResolvedValueOnce([])
      .mockResolvedValue([
        {
          id: 1,
          time: '08:00',
          enabled: true,
          medications: [
            {
              id: 11,
              name: '降压药',
              alias: '小白片',
              dosageValue: 1,
              dosageUnit: '片',
              usage: '饭后',
              notes: '',
              photoUrl: '',
              enableOcr: false,
              enableYolo: false,
              ocrEndpoint: '',
              yoloEndpoint: '',
              enabled: true,
            },
          ],
        },
      ])

    const view = render(TestHarness)

    await flushPromises()
    expect(mockGetMedicationAlarms).toHaveBeenCalledTimes(1)
    expect(mockWarning).not.toHaveBeenCalled()

    window.dispatchEvent(new Event(MEDICATION_ALARM_CHANGED_EVENT))

    await flushPromises()
    expect(mockGetMedicationAlarms).toHaveBeenCalledTimes(2)
    expect(mockWarning).toHaveBeenCalledWith('到点服药提醒', '请立即服用：小白片', 18000)
    expect(document.querySelector('[data-medication-alarm-overlay="true"]')).not.toBeNull()

    window.dispatchEvent(new Event(MEDICATION_ALARM_CHANGED_EVENT))

    await flushPromises()
    expect(mockGetMedicationAlarms).toHaveBeenCalledTimes(3)
    expect(mockWarning).toHaveBeenCalledTimes(1)

    view.unmount()
  })
})
