import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RehabPlan, RehabPlanSettings, RehabVideoResult } from '@/api/modules/rehab'
import RehabPage from './RehabPage.vue'

const {
  mockCreateRehabVideoTask,
  mockGetRehabPlan,
  mockGetRehabPlanSettings,
  mockGetRehabVideoTask,
  mockRemoveRehabExercise,
  mockSaveRehabPlanSettings,
  mockToggleRehabExercise,
} = vi.hoisted(() => ({
  mockCreateRehabVideoTask: vi.fn(),
  mockGetRehabPlan: vi.fn<() => Promise<RehabPlan>>(),
  mockGetRehabPlanSettings: vi.fn<() => Promise<RehabPlanSettings>>(),
  mockGetRehabVideoTask: vi.fn<() => Promise<RehabVideoResult>>(),
  mockRemoveRehabExercise: vi.fn<() => Promise<RehabPlan>>(),
  mockSaveRehabPlanSettings: vi.fn(),
  mockToggleRehabExercise: vi.fn<() => Promise<RehabPlan>>(),
}))

vi.mock('@/api/modules/rehab', () => ({
  createRehabVideoTask: mockCreateRehabVideoTask,
  getRehabPlan: mockGetRehabPlan,
  getRehabPlanSettings: mockGetRehabPlanSettings,
  getRehabVideoTask: mockGetRehabVideoTask,
  removeRehabExercise: mockRemoveRehabExercise,
  saveRehabPlanSettings: mockSaveRehabPlanSettings,
  toggleRehabExercise: mockToggleRehabExercise,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/shared/components/EChartCanvas.vue', () => ({
  default: {
    template: '<div data-testid="rehab-week-chart" />',
  },
}))

const rehabPlanFixture: RehabPlan = {
  label: '今日计划',
  exercises: [
    {
      id: 1,
      name: '鸟狗式',
      category: '核心稳定',
      duration: '3 组 × 12 次',
      level: '基础',
      minutes: 8,
      steps: ['保持脊柱中立位', '对侧手脚伸直'],
      caution: '腰部明显疼痛时停止。',
      focus: '核心稳定',
      benefits: ['提升稳定性'],
      videoMinutes: 6,
      done: false,
    },
  ],
  weekTrend: {
    labels: ['03-12'],
    values: [20],
    insight: '本周训练执行稳定。',
    deltaPercent: 8,
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

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/home', component: { template: '<div>home page</div>' } },
      { path: '/rehab', component: RehabPage },
      { path: '/rehab/exercise', component: { template: '<div>exercise page</div>' } },
      { path: '/rehab/reminder', component: { template: '<div>reminder page</div>' } },
    ],
  })
  router.push('/rehab')
  await router.isReady()

  const result = render(RehabPage, {
    global: {
      plugins: [pinia, router],
    },
  })

  return { ...result, router }
}

describe('RehabPage', () => {
  beforeEach(() => {
    mockCreateRehabVideoTask.mockReset()
    mockGetRehabPlan.mockReset()
    mockGetRehabPlanSettings.mockReset()
    mockGetRehabVideoTask.mockReset()
    mockRemoveRehabExercise.mockReset()
    mockSaveRehabPlanSettings.mockReset()
    mockToggleRehabExercise.mockReset()
    mockGetRehabPlan.mockResolvedValue(rehabPlanFixture)
    mockGetRehabPlanSettings.mockResolvedValue(rehabPlanFixture.planSummary)
  })

  it('renders the Apple-style rehab header and plan actions', async () => {
    await renderPage()

    expect(screen.getByRole('heading', { name: '康复训练' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '调整' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '智能计划' })).toBeInTheDocument()
  })

  it('renders rehab summary and supports marking exercise done', async () => {
    mockToggleRehabExercise.mockResolvedValue({
      ...rehabPlanFixture,
      exercises: rehabPlanFixture.exercises.map((item) => ({ ...item, done: true })),
    })

    await renderPage()

    expect(await screen.findByText('今日完成度')).toBeInTheDocument()
    expect(screen.getByText('鸟狗式')).toBeInTheDocument()

    await fireEvent.click(screen.getByLabelText('标记完成'))

    await waitFor(() => expect(mockToggleRehabExercise).toHaveBeenCalledWith(1))
  })

  it('opens plan settings and saves updated summary', async () => {
    mockSaveRehabPlanSettings.mockResolvedValue({
      focus: '姿势修复',
      frequency: '每周 4 次',
      duration: '单次 25 分钟',
      intensity: '中等',
    })

    await renderPage()
    await screen.findByText('训练计划')

    await fireEvent.click(screen.getByRole('button', { name: '调整' }))
    await fireEvent.update(screen.getByDisplayValue('核心稳定'), '姿势修复')
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(mockSaveRehabPlanSettings).toHaveBeenCalledWith({
        focus: '姿势修复',
        frequency: '每周 3 次',
        duration: '单次 20 分钟',
        intensity: '低中强度',
      }),
    )
  })

  it('supports removing an exercise from today list', async () => {
    mockRemoveRehabExercise.mockResolvedValue({
      ...rehabPlanFixture,
      exercises: [],
    })

    await renderPage()
    await screen.findByText('鸟狗式')

    await fireEvent.click(screen.getByRole('button', { name: '删除' }))

    await waitFor(() => expect(mockRemoveRehabExercise).toHaveBeenCalledWith(1))
  })
})
