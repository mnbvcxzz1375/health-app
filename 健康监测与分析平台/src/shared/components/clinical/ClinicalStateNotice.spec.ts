import { fireEvent, render, screen } from '@testing-library/vue'
import ClinicalStateNotice from './ClinicalStateNotice.vue'

describe('ClinicalStateNotice', () => {
  it('renders content and action trigger', async () => {
    const onAction = vi.fn()

    render(ClinicalStateNotice, {
      props: {
        tone: 'error',
        title: '首页数据加载失败',
        description: '请稍后重试。',
        actionLabel: '重新加载',
        onAction,
      },
    })

    expect(screen.getByText('首页数据加载失败')).toBeInTheDocument()
    expect(screen.getByText('请稍后重试。')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '重新加载' }))

    expect(onAction).toHaveBeenCalledTimes(1)
  })
})
