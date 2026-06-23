import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import RehabReminderPage from './RehabReminderPage.vue'

const { mockGetRehabPlanReminder, mockSaveRehabPlanReminder, mockSuccess, mockWarning } = vi.hoisted(() => ({
  mockGetRehabPlanReminder: vi.fn(),
  mockSaveRehabPlanReminder: vi.fn(),
  mockSuccess: vi.fn(),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/modules/rehab', () => ({
  getRehabPlanReminder: mockGetRehabPlanReminder,
  saveRehabPlanReminder: mockSaveRehabPlanReminder,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: mockSuccess,
    warning: mockWarning,
    error: vi.fn(),
  }),
}))

async function renderPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rehab', component: { template: '<div>rehab page</div>' } },
      { path: '/rehab/reminder', component: RehabReminderPage },
    ],
  })

  router.push('/rehab/reminder')
  await router.isReady()

  const view = render(RehabReminderPage, {
    global: {
      plugins: [router],
    },
  })

  return { router, ...view }
}

describe('RehabReminderPage', () => {
  beforeEach(() => {
    mockGetRehabPlanReminder.mockReset()
    mockSaveRehabPlanReminder.mockReset()
    mockSuccess.mockReset()
    mockWarning.mockReset()
    mockGetRehabPlanReminder.mockResolvedValue({
      time: '08:00',
      days: ['mon', 'wed', 'fri'],
      pushEnabled: true,
    })
  })

  it('renders the feature nav bar and returns to rehab', async () => {
    const { router } = await renderPage()

    expect(await screen.findByRole('navigation', { name: '功能页导航' })).toBeInTheDocument()
    expect(screen.getAllByText('康复计划提醒').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '返回' })).toHaveLength(1)
    expect(screen.getByRole('button', { name: '返回计划' })).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '返回' }))

    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/rehab'))
  })

  it('still saves reminder settings from the page', async () => {
    mockSaveRehabPlanReminder.mockResolvedValue(undefined)

    await renderPage()

    await screen.findByText('系统通知')
    await fireEvent.click(screen.getByRole('button', { name: '保存提醒' }))

    await waitFor(() =>
      expect(mockSaveRehabPlanReminder).toHaveBeenCalledWith({
        time: '08:00',
        days: ['mon', 'wed', 'fri'],
        pushEnabled: true,
      }),
    )
  })
})
