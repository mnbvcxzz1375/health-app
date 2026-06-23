import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import UploadPage from './UploadPage.vue'

const {
  mockApplyRehabPlanDraft,
  mockCreateAnalyzeTask,
  mockDiscardAnalyzeReport,
  mockGetAnalyzeResult,
  mockSaveAnalyzeReport,
  mockWarning,
} = vi.hoisted(() => ({
  mockApplyRehabPlanDraft: vi.fn(),
  mockCreateAnalyzeTask: vi.fn(),
  mockDiscardAnalyzeReport: vi.fn(),
  mockGetAnalyzeResult: vi.fn(),
  mockSaveAnalyzeReport: vi.fn(),
  mockWarning: vi.fn(),
}))

vi.mock('@/api/modules/upload', () => ({
  createAnalyzeTask: mockCreateAnalyzeTask,
  discardAnalyzeReport: mockDiscardAnalyzeReport,
  getAnalyzeResult: mockGetAnalyzeResult,
  saveAnalyzeReport: mockSaveAnalyzeReport,
}))

vi.mock('@/api/modules/rehab', () => ({
  applyRehabPlanDraft: mockApplyRehabPlanDraft,
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({
    success: vi.fn(),
    info: vi.fn(),
    warning: mockWarning,
    error: vi.fn(),
  }),
}))

async function renderPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/upload', component: UploadPage },
      { path: '/home', component: { template: '<div>home page</div>' } },
      { path: '/rehab', component: { template: '<div>rehab page</div>' } },
    ],
  })

  router.push('/upload')
  await router.isReady()

  const view = render(UploadPage, {
    global: {
      plugins: [router],
    },
  })

  return { router, ...view }
}

const mockReport = {
  title: '化验报告分析报告',
  summary: '已根据当前资料生成结构化分析。',
  riskLevel: '中等风险',
  points: ['关注点一', '关注点二'],
  advice: ['建议一', '建议二'],
  rehabFocus: '先以低强度恢复为主。',
  followUp: ['继续观察 3 到 7 天。', '必要时补充检查。'],
  caution: '仅用于健康管理辅助。',
}

const mockDraft = {
  sourceTaskIds: ['task_1'],
  summary: {
    focus: '核心稳定',
    frequency: '每周 3 次',
    duration: '单次 20 分钟',
    intensity: '低到中等强度',
  },
  exercises: [
    {
      mode: 'existing',
      name: '鸟狗式',
      category: '核心稳定',
      duration: '3 组 × 12 次',
      level: '基础',
      minutes: 8,
      steps: ['保持脊柱中立位'],
      caution: '疼痛时停止。',
      focus: '核心稳定与抗旋转控制',
      benefits: ['提升稳定性'],
      videoMinutes: 6,
    },
  ],
  reminder: {
    time: '08:00',
    days: ['mon', 'wed', 'fri'],
    pushEnabled: true,
  },
} as const

describe('UploadPage', () => {
  beforeEach(() => {
    mockApplyRehabPlanDraft.mockReset()
    mockCreateAnalyzeTask.mockReset()
    mockDiscardAnalyzeReport.mockReset()
    mockGetAnalyzeResult.mockReset()
    mockSaveAnalyzeReport.mockReset()
    mockWarning.mockReset()
  })

  it('renders the feature nav bar and returns to home', async () => {
    const { router } = await renderPage()

    expect(screen.getByRole('navigation', { name: '功能页导航' })).toBeInTheDocument()
    expect(screen.getAllByText('上传分析').length).toBeGreaterThan(0)

    await fireEvent.click(screen.getByRole('button', { name: '返回' }))

    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/home'))
  })

  it('uses custom file picker instead of native choose file text', async () => {
    await renderPage()

    await fireEvent.click(screen.getByRole('button', { name: /影像资料/ }))

    const input = screen.getByTestId('upload-file-input') as HTMLInputElement
    const file = new File(['hello'], 'report.pdf', { type: 'application/pdf' })
    await fireEvent.change(input, { target: { files: [file] } })

    await waitFor(() => expect(screen.getByText('report.pdf')).toBeInTheDocument())
    expect(screen.getByText('重新选择文件')).toBeInTheDocument()
    expect(screen.queryByText('Choose File')).not.toBeInTheDocument()
  })

  it('submits text content and shows generated report', async () => {
    mockCreateAnalyzeTask.mockResolvedValue({ taskId: 'task_1' })
    mockGetAnalyzeResult.mockResolvedValue({
      status: 'DONE',
      report: mockReport,
      saved: false,
    })

    await renderPage()

    await fireEvent.click(screen.getByRole('button', { name: /文字报告/ }))
    await fireEvent.update(screen.getByRole('textbox'), '这是用于测试上传分析功能的文本内容，长度已经足够。')
    await fireEvent.click(screen.getByRole('button', { name: '开始分析' }))

    await waitFor(() => expect(mockCreateAnalyzeTask).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('化验报告分析报告')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保留并生成康复计划' })).toBeInTheDocument()
  })

  it('shows rehab plan draft after saving the generated report', async () => {
    mockCreateAnalyzeTask.mockResolvedValue({ taskId: 'task_1' })
    mockGetAnalyzeResult.mockResolvedValue({
      status: 'DONE',
      report: mockReport,
      saved: false,
    })
    mockSaveAnalyzeReport.mockResolvedValue({
      success: true,
      saved: true,
      rehabPlanDraft: mockDraft,
    })

    await renderPage()

    await fireEvent.click(screen.getByRole('button', { name: /症状描述/ }))
    await fireEvent.update(screen.getByRole('textbox'), '最近一周久坐后腰背发紧，下午更明显，偶尔伴随疲劳。')
    await fireEvent.click(screen.getByRole('button', { name: '开始分析' }))
    await screen.findByText('化验报告分析报告')

    await fireEvent.click(screen.getByRole('button', { name: '保留并生成康复计划' }))

    await waitFor(() => expect(mockSaveAnalyzeReport).toHaveBeenCalledWith('task_1'))
    expect(await screen.findByText('康复计划草案')).toBeInTheDocument()
    expect(screen.getByText('鸟狗式')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '应用到康复计划' })).toBeInTheDocument()
  })

  it('applies rehab draft only after explicit confirmation', async () => {
    mockCreateAnalyzeTask.mockResolvedValue({ taskId: 'task_1' })
    mockGetAnalyzeResult.mockResolvedValue({
      status: 'DONE',
      report: mockReport,
      saved: false,
    })
    mockSaveAnalyzeReport.mockResolvedValue({
      success: true,
      saved: true,
      rehabPlanDraft: mockDraft,
    })
    mockApplyRehabPlanDraft.mockResolvedValue({})

    await renderPage()

    await fireEvent.click(screen.getByRole('button', { name: /症状描述/ }))
    await fireEvent.update(screen.getByRole('textbox'), '最近一周久坐后腰背发紧，下午更明显，偶尔伴随疲劳。')
    await fireEvent.click(screen.getByRole('button', { name: '开始分析' }))
    await screen.findByText('化验报告分析报告')
    await fireEvent.click(screen.getByRole('button', { name: '保留并生成康复计划' }))

    expect(mockApplyRehabPlanDraft).not.toHaveBeenCalled()

    await fireEvent.click(await screen.findByRole('button', { name: '应用到康复计划' }))

    await waitFor(() => expect(mockApplyRehabPlanDraft).toHaveBeenCalledWith(mockDraft))
  })

  it('shows guidance instead of silent failure when content is incomplete', async () => {
    await renderPage()

    await fireEvent.click(screen.getByRole('button', { name: '开始分析' }))

    expect(mockWarning).toHaveBeenCalled()
    expect(mockCreateAnalyzeTask).not.toHaveBeenCalled()
  })
})
