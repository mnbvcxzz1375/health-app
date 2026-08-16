import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createVuetify } from 'vuetify'
import type { MedicationAlarm, TodayScheduleResponse } from '@/api/modules/medication'
import MedicationPage from './MedicationPage.vue'

const {
  mockConfirmIntake,
  mockDeleteMedicationAlarm,
  mockError,
  mockGetMedicationAlarms,
  mockGetTodaySchedule,
  mockRecognizeBatch,
  mockExplainMedication,
  mockSuccess,
  mockToggleMedicationAlarm,
} = vi.hoisted(() => ({
  mockConfirmIntake: vi.fn(),
  mockDeleteMedicationAlarm: vi.fn(),
  mockError: vi.fn(),
  mockGetMedicationAlarms: vi.fn<() => Promise<MedicationAlarm[]>>(),
  mockGetTodaySchedule: vi.fn<() => Promise<TodayScheduleResponse>>(),
  mockRecognizeBatch: vi.fn(),
  mockExplainMedication: vi.fn(),
  mockSuccess: vi.fn(),
  mockToggleMedicationAlarm: vi.fn(),
}))

vi.mock('@/api/modules/medication', () => ({
  confirmMedicationIntake: mockConfirmIntake,
  deleteMedicationAlarm: mockDeleteMedicationAlarm,
  explainMedication: mockExplainMedication,
  getMedicationAlarms: mockGetMedicationAlarms,
  getTodayMedicationSchedule: mockGetTodaySchedule,
  recognizeMedicationBatch: mockRecognizeBatch,
  toggleMedicationAlarm: mockToggleMedicationAlarm,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: mockSuccess,
    warning: vi.fn(),
    info: vi.fn(),
    error: mockError,
  }),
}))

const alarmFixture: MedicationAlarm[] = [
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
        notes: '避免与牛奶同服',
        photoUrl: '',
        enableOcr: false,
        enableYolo: false,
        ocrEndpoint: '',
        yoloEndpoint: '',
        enabled: true,
      },
    ],
  },
]

const todayFixture: TodayScheduleResponse = {
  date: '2026-06-20',
  items: [
    {
      alarmId: 1,
      time: '08:00',
      enabled: true,
      medications: alarmFixture[0].medications,
      intakeStatus: 'pending',
    },
  ],
  totalCount: 1,
  completedCount: 0,
}

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const vuetify = createVuetify()

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/home', component: { template: '<div>home page</div>' } },
      { path: '/medication', component: MedicationPage },
      { path: '/medication/alarm', component: { template: '<div>alarm page</div>' } },
    ],
  })
  router.push('/medication')
  await router.isReady()

  const view = render(MedicationPage, {
    global: {
      plugins: [pinia, router, vuetify],
    },
  })

  return { router, ...view }
}

describe('MedicationPage', () => {
  beforeEach(() => {
    mockConfirmIntake.mockReset()
    mockDeleteMedicationAlarm.mockReset()
    mockError.mockReset()
    mockGetMedicationAlarms.mockReset()
    mockGetTodaySchedule.mockReset()
    mockRecognizeBatch.mockReset()
    mockExplainMedication.mockReset()
    mockSuccess.mockReset()
    mockToggleMedicationAlarm.mockReset()
    mockGetMedicationAlarms.mockResolvedValue(alarmFixture)
    mockGetTodaySchedule.mockResolvedValue(todayFixture)
  })

  it('renders today schedule by default and loads data', async () => {
    await renderPage()
    await waitFor(() => expect(mockGetTodaySchedule).toHaveBeenCalled())
    expect(screen.getByRole('heading', { name: '今日服药' })).toBeInTheDocument()
  })

  it('shows intake confirmation buttons for pending items', async () => {
    await renderPage()
    await waitFor(() => expect(mockGetTodaySchedule).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByText('已服用')).toBeInTheDocument())
    expect(screen.getByText('跳过')).toBeInTheDocument()
  })

  it('confirms medication intake when button clicked', async () => {
    mockConfirmIntake.mockResolvedValue({ success: true, alarmId: 1, status: 'taken' })

    await renderPage()
    await waitFor(() => expect(mockGetTodaySchedule).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByText('已服用')).toBeInTheDocument())

    await fireEvent.click(screen.getByText('已服用'))
    await waitFor(() => expect(mockConfirmIntake).toHaveBeenCalledWith(1, 'taken'))
    await waitFor(() => expect(mockSuccess).toHaveBeenCalledWith('确认成功', '已服用'))
  })

  it('renders all three tabs', async () => {
    await renderPage()
    await waitFor(() => expect(mockGetTodaySchedule).toHaveBeenCalled())
    expect(screen.getByText('扫描识别')).toBeInTheDocument()
    expect(screen.getByText('用药闹钟')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '今日服药' })).toBeInTheDocument()
  })

  it('renders elder mode toggle', async () => {
    await renderPage()
    await waitFor(() => expect(mockGetTodaySchedule).toHaveBeenCalled())
    await fireEvent.click(screen.getByRole('button', { name: '标准' }))
    expect(screen.getByRole('button', { name: '老人模式' })).toBeInTheDocument()
  })
})
