import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ClinicalFeatureNavBar from './ClinicalFeatureNavBar.vue'

async function renderBar() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/current', component: { template: '<div>current page</div>' } },
      { path: '/home', component: { template: '<div>home page</div>' } },
    ],
  })

  router.push('/current')
  await router.isReady()

  const view = render(ClinicalFeatureNavBar, {
    props: {
      title: '用药提醒',
      backTo: '/home',
    },
    global: {
      plugins: [router],
    },
  })

  return { router, ...view }
}

describe('ClinicalFeatureNavBar', () => {
  it('renders the back button, centered title, and placeholder spacer', async () => {
    const { container } = await renderBar()

    expect(screen.getByRole('navigation', { name: '功能页导航' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '返回' })).toBeInTheDocument()
    expect(screen.getByText('用药提醒')).toBeInTheDocument()
    expect(screen.getByTestId('feature-nav-spacer')).toBeInTheDocument()
    expect(container.querySelector('nav')).toHaveStyle({ width: '75%' })
  })

  it('navigates to the configured target when clicking back', async () => {
    const { router } = await renderBar()

    await fireEvent.click(screen.getByRole('button', { name: '返回' }))

    await waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/home'))
  })
})
