import { render, screen } from '@testing-library/vue'
import { RouterLinkStub } from '@vue/test-utils'
import AssistantFloatingEntry from '@/shared/components/assistant/AssistantFloatingEntry.vue'

describe('AssistantFloatingEntry', () => {
  it('renders floating assistant entry', () => {
    render(AssistantFloatingEntry, {
      props: { visible: true },
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    })

    expect(screen.getByLabelText('打开智能助手')).toBeInTheDocument()
    expect(screen.getByText('智能助手')).toBeInTheDocument()
  })
})
