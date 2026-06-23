import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { MedicationAlarm, MedicationRecognitionBatchResult } from '@/api/modules/medication'
import { MEDICATION_ALARM_CHANGED_EVENT } from '@/modules/medication/utils/medicationAlarm'
import MedicationAlarmPage from './MedicationAlarmPage.vue'

const {
  mockCreateMedicationAlarm,
  mockError,
  mockGetMedicationAlarms,
  mockRecognizeMedicationBatch,
  mockSuccess,
  mockUpdateMedicationAlarm,
  mockWarning,
  mockNormalizeFilesForModel,
} = vi.hoisted(() => ({
  mockCreateMedicationAlarm: vi.fn(),
  mockError: vi.fn(),
  mockGetMedicationAlarms: vi.fn<() => Promise<MedicationAlarm[]>>(),
  mockRecognizeMedicationBatch: vi.fn<(files: File[]) => Promise<MedicationRecognitionBatchResult>>(),
  mockSuccess: vi.fn(),
  mockUpdateMedicationAlarm: vi.fn(),
  mockWarning: vi.fn(),
  mockNormalizeFilesForModel: vi.fn(async (files: File[]) => files),
}))

vi.mock('@/api/modules/medication', () => ({
  createMedicationAlarm: mockCreateMedicationAlarm,
  getMedicationAlarms: mockGetMedicationAlarms,
  recognizeMedicationBatch: mockRecognizeMedicationBatch,
  updateMedicationAlarm: mockUpdateMedicationAlarm,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: mockSuccess,
    warning: mockWarning,
    info: vi.fn(),
    error: mockError,
  }),
}))

vi.mock('@/shared/utils/modelImage', () => ({
  normalizeFilesForModel: mockNormalizeFilesForModel,
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

async function renderPage(initialPath = '/medication/alarm') {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/medication', component: { template: '<div>medication list</div>' } },
      { path: '/medication/alarm', component: MedicationAlarmPage },
    ],
  })
  router.push(initialPath)
  await router.isReady()

  const view = render(MedicationAlarmPage, {
    global: {
      plugins: [pinia, router],
    },
  })

  return { router, ...view }
}

async function chooseOption(label: string, option: string, index = 0) {
  const triggers = screen.getAllByRole('button', { name: label })
  await fireEvent.click(triggers[index])
  await fireEvent.click(screen.getByRole('button', { name: option }))
}

describe('MedicationAlarmPage', () => {
  beforeEach(() => {
    mockCreateMedicationAlarm.mockReset()
    mockError.mockReset()
    mockGetMedicationAlarms.mockReset()
    mockNormalizeFilesForModel.mockClear()
    mockRecognizeMedicationBatch.mockReset()
    mockSuccess.mockReset()
    mockUpdateMedicationAlarm.mockReset()
    mockWarning.mockReset()
    mockGetMedicationAlarms.mockResolvedValue(alarmFixture)
    Object.defineProperty(window, 'alert', {
      configurable: true,
      writable: true,
      value: vi.fn(),
    })
  })

  it('renders the feature nav bar and returns to the medication list', async () => {
    const { router } = await renderPage()

    expect(await screen.findByRole('navigation', { name: '功能页导航' })).toBeInTheDocument()
    expect(screen.getAllByText('新增闹钟').length).toBeGreaterThan(0)

    await fireEvent.click(screen.getByRole('button', { name: '返回' }))

    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/medication'))
  })

  it('creates a new alarm and returns to the medication list', async () => {
    mockCreateMedicationAlarm.mockResolvedValue({
      id: 2,
      time: '21:00',
      enabled: true,
      medications: [
        {
          id: 21,
          name: '维生素D',
          alias: '小黄瓶',
          dosageValue: 1,
          dosageUnit: '粒',
          usage: '饭后',
          notes: '',
          photoUrl: '',
          enableOcr: false,
          enableYolo: false,
          ocrEndpoint: '',
          yoloEndpoint: '',
          enabled: true,
        },
        {
          id: 22,
          name: '鱼油',
          alias: '',
          dosageValue: 2,
          dosageUnit: '粒',
          usage: '随餐',
          notes: '',
          photoUrl: '',
          enableOcr: false,
          enableYolo: false,
          ocrEndpoint: '',
          yoloEndpoint: '',
          enabled: true,
        },
      ],
    })
    const changedHandler = vi.fn()
    window.addEventListener(MEDICATION_ALARM_CHANGED_EVENT, changedHandler)

    const { router } = await renderPage()

    await screen.findByRole('navigation', { name: '功能页导航' })
    await chooseOption('提醒小时', '21')
    await chooseOption('提醒分钟', '00')
    await fireEvent.update(screen.getAllByPlaceholderText('例如：降压药')[0], '维生素D')
    await fireEvent.update(screen.getAllByPlaceholderText('例如：小白片')[0], '小黄瓶')
    await chooseOption('剂量', '1')
    await chooseOption('单位', '粒')
    await chooseOption('服用方式', '饭后')

    await fireEvent.click(screen.getByRole('button', { name: '新增药品' }))

    const nameInputs = screen.getAllByPlaceholderText('例如：降压药')
    const aliasInputs = screen.getAllByPlaceholderText('例如：小白片')
    await fireEvent.update(nameInputs[0], '鱼油')
    await fireEvent.update(aliasInputs[0], '')
    await chooseOption('剂量', '2')
    await chooseOption('单位', '粒')
    await chooseOption('服用方式', '随餐')

    await fireEvent.click(screen.getByRole('button', { name: '保存闹钟' }))

    await waitFor(() => expect(mockCreateMedicationAlarm).toHaveBeenCalledTimes(1))
    expect(mockCreateMedicationAlarm.mock.calls[0][0]).toMatchObject({
      time: '21:00',
      medications: [
        expect.objectContaining({ name: '鱼油' }),
        expect.objectContaining({ name: '维生素D', alias: '小黄瓶' }),
      ],
    })
    await waitFor(() => expect(changedHandler).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/medication'))

    window.removeEventListener(MEDICATION_ALARM_CHANGED_EVENT, changedHandler)
  })

  it('loads an existing alarm by route query, updates it, and returns to the list', async () => {
    mockUpdateMedicationAlarm.mockResolvedValue(alarmFixture[0])
    const changedHandler = vi.fn()
    window.addEventListener(MEDICATION_ALARM_CHANGED_EVENT, changedHandler)

    const { router } = await renderPage('/medication/alarm?id=1')

    expect(await screen.findByDisplayValue('降压药')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '保存闹钟' }))

    await waitFor(() => expect(mockUpdateMedicationAlarm).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(changedHandler).toHaveBeenCalledTimes(1))
    expect(mockSuccess).toHaveBeenCalledWith('闹钟修改成功')
    expect(window.alert).toHaveBeenCalledWith('闹钟修改成功')
    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/medication'))

    window.removeEventListener(MEDICATION_ALARM_CHANGED_EVENT, changedHandler)
  })

  it('blocks saving when recognition review is not confirmed', async () => {
    mockRecognizeMedicationBatch.mockResolvedValue({
      items: [
        {
          name: '布洛芬缓释胶囊',
          alias: '',
          dosageValue: 1,
          dosageUnit: '粒',
          usage: '饭后',
          notes: '',
          photoUrl: '',
          confidence: 0.91,
        },
      ],
      confidence: 0.91,
    })

    await renderPage()

    const input = screen.getByTestId('medication-upload-input') as HTMLInputElement
    const file = new File(['img-a'], 'pill-a.png', { type: 'image/png' })
    await fireEvent.change(input, { target: { files: [file] } })
    await fireEvent.click(screen.getByRole('button', { name: '开始识别' }))
    await screen.findByText('识别完成后请逐项核对，确认无误后再保存闹钟。')

    await fireEvent.click(screen.getByRole('button', { name: '保存闹钟' }))

    expect(mockCreateMedicationAlarm).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalled()
    expect(screen.getByText('请先勾选“我已核对识别结果”，再保存闹钟。')).toBeInTheDocument()
  })

  it('redirects back to the medication list when the alarm id is not found', async () => {
    const { router } = await renderPage('/medication/alarm?id=999')

    await waitFor(() => expect(mockGetMedicationAlarms).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/medication'))
    expect(mockWarning).toHaveBeenCalledWith('未找到对应闹钟', '请从用药提醒列表重新进入。')
  })
})
