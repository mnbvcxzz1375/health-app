import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RehabExercise } from '@/api/modules/rehab'
import RehabExercisePage from './RehabExercisePage.vue'

const { mockGetRehabExerciseByName, mockSuccess, mockWarning } = vi.hoisted(() => ({
  mockGetRehabExerciseByName: vi.fn<() => Promise<RehabExercise>>(),
  mockSuccess: vi.fn(),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/modules/rehab', () => ({
  getRehabExerciseByName: mockGetRehabExerciseByName,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: mockSuccess,
    warning: mockWarning,
    error: vi.fn(),
  }),
}))

const exerciseFixture: RehabExercise = {
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
}

async function renderPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rehab', component: { template: '<div>rehab page</div>' } },
      { path: '/rehab/reminder', component: { template: '<div>reminder page</div>' } },
      { path: '/rehab/exercise', component: RehabExercisePage },
    ],
  })

  router.push({ path: '/rehab/exercise', query: { name: '鸟狗式' } })
  await router.isReady()

  const view = render(RehabExercisePage, {
    global: {
      plugins: [router],
    },
  })

  return { router, ...view }
}

describe('RehabExercisePage', () => {
  beforeEach(() => {
    mockGetRehabExerciseByName.mockReset()
    mockSuccess.mockReset()
    mockWarning.mockReset()
    mockGetRehabExerciseByName.mockResolvedValue(exerciseFixture)
  })

  it('renders the feature nav bar, keeps action buttons, and returns to rehab', async () => {
    const { router } = await renderPage()

    expect(await screen.findByRole('navigation', { name: '功能页导航' })).toBeInTheDocument()
    expect(screen.getAllByText('动作详情').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '返回' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: '设置提醒' })).toHaveLength(2)

    await fireEvent.click(screen.getByRole('button', { name: '返回' }))

    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/rehab'))
  })

  it('still navigates to reminder settings from the exercise page', async () => {
    const { router } = await renderPage()

    await screen.findByText('鸟狗式')
    await fireEvent.click(screen.getAllByRole('button', { name: '设置提醒' })[0])

    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/rehab/reminder'))
  })
})
