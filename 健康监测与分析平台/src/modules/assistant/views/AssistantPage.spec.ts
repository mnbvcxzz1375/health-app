import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import AssistantPage from '@/modules/assistant/views/AssistantPage.vue'

const mockStreamConsultQuestion = vi.fn()
const mockStreamConsultSSE = vi.fn()

vi.mock('@/api/modules/consult', () => ({
  streamConsultQuestion: (...args: unknown[]) => mockStreamConsultQuestion(...args),
  streamConsultSSE: (...args: unknown[]) => mockStreamConsultSSE(...args),
  getConsultHistory: vi.fn().mockResolvedValue([]),
  deleteConsultHistory: vi.fn(),
  clearConsultHistory: vi.fn(),
}))

async function renderPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/assistant', component: AssistantPage },
      { path: '/home', component: { template: '<div>home</div>' } },
    ],
  })

  router.push('/assistant')
  await router.isReady()

  return render(AssistantPage, {
    global: {
      plugins: [router],
    },
  })
}

describe('AssistantPage', () => {
  beforeEach(() => {
    mockStreamConsultQuestion.mockReset()
    mockStreamConsultSSE.mockReset()
    mockStreamConsultSSE.mockImplementation(async function* () {})
    window.localStorage.clear()
  })

  it('asks directly when clicking preset question', async () => {
    mockStreamConsultQuestion.mockImplementation(async (_payload, handlers) => {
      handlers.onChunk?.('今天建议先做')
      handlers.onChunk?.('低强度核心稳定训练。')
      handlers.onComplete?.({
        requestId: 'consult-1',
        answer: '今天建议先做低强度核心稳定训练。',
        suggestions: ['先热身 5 分钟', '训练后拉伸', '今晚提前休息'],
        disclaimer: '该回答仅用于健康管理辅助，不替代医生诊疗与处方。',
      })
    })

    await renderPage()

    await fireEvent.click(screen.getAllByRole('button', { name: '我今天适合做什么强度的训练？' }).at(-1)!)

    await waitFor(() => {
      expect(mockStreamConsultQuestion).toHaveBeenCalledTimes(1)
    })

    expect(await screen.findByText('今天建议先做低强度核心稳定训练。')).toBeInTheDocument()
  })

  it('restores local history on mount', async () => {
    window.localStorage.setItem(
      'hm_assistant_history',
      JSON.stringify([
        {
          id: 'old-1',
          role: 'user',
          content: '昨晚睡得不好怎么办？',
        },
        {
          id: 'old-2',
          role: 'assistant',
          content: '今晚可以先减少屏幕刺激，再提前 30 分钟放松。',
        },
      ]),
    )

    await renderPage()

    expect(screen.getByText('昨晚睡得不好怎么办？')).toBeInTheDocument()
    expect(screen.getByText('今晚可以先减少屏幕刺激，再提前 30 分钟放松。')).toBeInTheDocument()
  })
})
