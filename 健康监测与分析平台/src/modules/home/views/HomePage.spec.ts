import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { DeviceItem } from '@/api/modules/device'
import type { HomeSummary } from '@/api/modules/home'
import type { MonitorLatest, MonitorTrend } from '@/api/modules/monitor'
import type { RehabPlan } from '@/api/modules/rehab'
import type { SavedAnalyzeReport } from '@/api/modules/upload'
import { useAuthStore } from '@/stores/auth'
import HomePage from './HomePage.vue'

const {
  mockAskConsultQuestion,
  mockCreateDevice,
  mockDeleteDevice,
  mockGetDevices,
  mockGetHomeSummary,
  mockGetMonitorLatest,
  mockGetMonitorTrend,
  mockGetRehabPlan,
  mockGetSavedAnalyzeReports,
  mockSyncDevice,
} = vi.hoisted(() => ({
  mockAskConsultQuestion: vi.fn(),
  mockCreateDevice: vi.fn(),
  mockDeleteDevice: vi.fn(),
  mockGetDevices: vi.fn<() => Promise<DeviceItem[]>>(),
  mockGetHomeSummary: vi.fn<() => Promise<HomeSummary>>(),
  mockGetMonitorLatest: vi.fn<() => Promise<MonitorLatest>>(),
  mockGetMonitorTrend: vi.fn<() => Promise<MonitorTrend>>(),
  mockGetRehabPlan: vi.fn<() => Promise<RehabPlan>>(),
  mockGetSavedAnalyzeReports: vi.fn<() => Promise<SavedAnalyzeReport[]>>(),
  mockSyncDevice: vi.fn(),
}))

vi.mock('@/api/modules/home', () => ({
  getHomeSummary: mockGetHomeSummary,
}))

vi.mock('@/api/modules/rehab', () => ({
  getRehabPlan: mockGetRehabPlan,
}))

vi.mock('@/api/modules/monitor', () => ({
  getMonitorLatest: mockGetMonitorLatest,
  getMonitorTrend: mockGetMonitorTrend,
}))

vi.mock('@/api/modules/device', () => ({
  createDevice: mockCreateDevice,
  deleteDevice: mockDeleteDevice,
  getDevices: mockGetDevices,
  syncDevice: mockSyncDevice,
}))

vi.mock('@/api/modules/consult', () => ({
  askConsultQuestion: mockAskConsultQuestion,
}))

vi.mock('@/api/modules/upload', () => ({
  getSavedAnalyzeReports: mockGetSavedAnalyzeReports,
  discardAnalyzeReport: vi.fn(),
}))

vi.mock('@/shared/components/EChartCanvas.vue', () => ({
  default: {
    template: '<div data-testid="mock-chart"></div>',
    props: ['option'],
  },
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

const summaryFixture: HomeSummary = {
  userName: '李明',
  healthScore: 84,
  statusBadge: '总体稳定',
  statusBadgeVariant: 'success',
  statusSummary: '适合完成今日核心训练',
  stepsTarget: 9000,
  stepsNow: 5320,
  keyMetrics: [
    { key: 'hr', value: 71, badge: '正常', badgeVariant: 'success', hint: '静息心率较昨日下降 1' },
    { key: 'stress', value: 48, badge: '平稳', badgeVariant: 'success', hint: '压力处于可控区间' },
    { key: 'hydration', value: 1600, badge: '达标', badgeVariant: 'info', hint: '补水接近目标' },
  ],
  suggestions: ['优先完成核心训练', '晚间避免额外高强度活动'],
}

const rehabFixture: RehabPlan = {
  label: '今日计划',
  exercises: [
    {
      id: 1,
      name: '鸟狗式',
      category: '核心稳定',
      duration: '3 组 × 12 次',
      level: '基础',
      minutes: 8,
      steps: ['保持脊柱中立位'],
      caution: '腰部明显疼痛时停止。',
      focus: '核心稳定',
      benefits: ['提升稳定性'],
      videoMinutes: 6,
      done: false,
    },
  ],
  weekTrend: {
    labels: ['03-01'],
    values: [20],
    insight: '训练趋势稳定。',
    deltaPercent: 12,
  },
  planSummary: {
    focus: '核心稳定',
    frequency: '每周 3 次',
    duration: '单次 20 分钟',
    intensity: '低中强度',
  },
  reminderSummary: {
    time: '08:00',
    days: '周一 / 周三 / 周五',
    channel: '系统通知',
    status: '已开启',
  },
}

const latestFixture: MonitorLatest = {
  hr: 71,
  sleep: 83,
  deepSleep: 2,
  awake: 1,
  stress: 48,
  updatedAt: '2026-03-17T08:00:00.000Z',
}

const trendFixture: MonitorTrend = {
  labels: ['03-11', '03-12', '03-13'],
  values: [72, 70, 71],
  insight: '波动不大，整体稳定。',
  suggestion: '维持当前训练节奏。',
}

const deviceFixture: DeviceItem[] = [
  {
    id: 1,
    name: '腕部手表',
    brand: '华为',
    model: 'Watch 5',
    type: 'watch',
    connected: true,
    battery: 86,
    lastSyncAt: '2026-03-12T08:00:00.000Z',
  },
]

const savedReportFixture: SavedAnalyzeReport[] = [
  {
    taskId: 'task_saved_1',
    type: 'lab',
    fileName: '4-1.jpg',
    createdAt: '2026-03-17T08:00:00.000Z',
    updatedAt: '2026-03-17T09:00:00.000Z',
    report: {
      title: '化验报告分析报告',
      summary: '已保留最近一次化验分析结果。',
      riskLevel: '中等风险',
      points: ['注意炎症指标变化', '结合近期症状继续观察'],
      advice: ['保持清淡饮食', '必要时复查'],
      rehabFocus: '以低强度恢复训练为主',
      followUp: ['三天后复查体感'],
      caution: '仅用于健康管理辅助。',
    },
  },
]

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const authStore = useAuthStore()
  authStore.applySession({
    token: 'token',
    user: {
      id: '1',
      name: '李明',
      email: 'liming@example.com',
      avatarUrl: '',
    },
  })

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/home', component: HomePage },
      { path: '/upload', component: { template: '<div>upload page</div>' } },
      { path: '/rehab', component: { template: '<div>rehab page</div>' } },
      { path: '/medication', component: { template: '<div>medication page</div>' } },
    ],
  })
  router.push('/home')
  await router.isReady()

  return render(HomePage, {
    global: {
      plugins: [pinia, router],
    },
  })
}

describe('HomePage', () => {
  beforeEach(() => {
    mockAskConsultQuestion.mockReset()
    mockCreateDevice.mockReset()
    mockDeleteDevice.mockReset()
    mockGetDevices.mockReset()
    mockGetHomeSummary.mockReset()
    mockGetMonitorLatest.mockReset()
    mockGetMonitorTrend.mockReset()
    mockGetRehabPlan.mockReset()
    mockGetSavedAnalyzeReports.mockReset()
    mockSyncDevice.mockReset()
    mockGetMonitorLatest.mockResolvedValue(latestFixture)
    mockGetMonitorTrend.mockResolvedValue(trendFixture)
    mockGetSavedAnalyzeReports.mockResolvedValue([])
  })

  it('shows loading state before dashboard data resolves', async () => {
    const homeRequest = deferred<HomeSummary>()
    const rehabRequest = deferred<RehabPlan>()
    const deviceRequest = deferred<DeviceItem[]>()
    const reportRequest = deferred<SavedAnalyzeReport[]>()
    mockGetHomeSummary.mockReturnValue(homeRequest.promise)
    mockGetRehabPlan.mockReturnValue(rehabRequest.promise)
    mockGetDevices.mockReturnValue(deviceRequest.promise)
    mockGetSavedAnalyzeReports.mockReturnValue(reportRequest.promise)

    await renderPage()

    expect(screen.getByText('正在加载总览')).toBeInTheDocument()

    homeRequest.resolve(summaryFixture)
    rehabRequest.resolve(rehabFixture)
    deviceRequest.resolve(deviceFixture)
    reportRequest.resolve([])

    await waitFor(() => expect(screen.getAllByRole('button', { name: '上传资料' }).length).toBeGreaterThan(0))
  })

  it('renders merged overview, monitor section and saved reports', async () => {
    mockGetHomeSummary.mockResolvedValue(summaryFixture)
    mockGetRehabPlan.mockResolvedValue(rehabFixture)
    mockGetDevices.mockResolvedValue(deviceFixture)
    mockGetSavedAnalyzeReports.mockResolvedValue(savedReportFixture)

    await renderPage()

    await waitFor(() => expect(screen.getAllByRole('button', { name: '上传资料' }).length).toBeGreaterThan(0))
    expect(screen.getByText('监测趋势')).toBeInTheDocument()
    expect(screen.getByText('已保存报告')).toBeInTheDocument()
    expect(screen.getByText('已保存 1 份分析报告')).toBeInTheDocument()
    expect(screen.getByTestId('mock-chart')).toBeInTheDocument()

    await fireEvent.click(
      screen.getByRole('button', { name: /已保存 1 份分析报告.*展开后可查看最近结果。/ }),
    )
    expect(screen.getByText('化验报告分析报告')).toBeInTheDocument()
  })

  it('renders empty state when no metrics, no suggestions and no saved reports', async () => {
    mockGetHomeSummary.mockResolvedValue({
      ...summaryFixture,
      keyMetrics: [],
      suggestions: [],
    })
    mockGetRehabPlan.mockResolvedValue(rehabFixture)
    mockGetDevices.mockResolvedValue([])
    mockGetSavedAnalyzeReports.mockResolvedValue([])

    await renderPage()

    expect(await screen.findByText('暂未形成健康画像')).toBeInTheDocument()
  })

  it('renders error state and retries loading', async () => {
    mockGetHomeSummary.mockRejectedValueOnce(new Error('网络异常')).mockResolvedValueOnce(summaryFixture)
    mockGetRehabPlan.mockResolvedValue(rehabFixture)
    mockGetDevices.mockResolvedValue(deviceFixture)
    mockGetSavedAnalyzeReports.mockResolvedValue([])

    await renderPage()
    expect(await screen.findByText('首页加载失败')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '重新加载' }))
    await waitFor(() => expect(mockGetHomeSummary).toHaveBeenCalledTimes(2))
  })

  it('submits consult question and renders AI response', async () => {
    mockGetHomeSummary.mockResolvedValue(summaryFixture)
    mockGetRehabPlan.mockResolvedValue(rehabFixture)
    mockGetDevices.mockResolvedValue(deviceFixture)
    mockGetSavedAnalyzeReports.mockResolvedValue([])
    mockAskConsultQuestion.mockResolvedValue({
      requestId: 'consult_0001',
      answer: '建议先降低下午训练强度。',
      suggestions: ['下午减少 10 分钟训练', '晚间增加放松呼吸'],
      disclaimer: '该回答仅用于健康管理建议，不替代医生诊疗。',
    })

    await renderPage()
    await screen.findByText('健康问询')

    await fireEvent.update(screen.getByPlaceholderText('例如：下午总是疲劳怎么办？'), '下午总是疲劳怎么办？')
    await fireEvent.click(screen.getByRole('button', { name: '获取建议' }))

    await waitFor(() => expect(mockAskConsultQuestion).toHaveBeenCalled())
    expect(screen.getByText('建议先降低下午训练强度。')).toBeInTheDocument()
    expect(screen.getByText('晚间增加放松呼吸')).toBeInTheDocument()
  })
})
