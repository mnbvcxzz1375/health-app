import { needsModelImageTranscode } from './modelImage'

describe('modelImage', () => {
  it('marks avif as needing conversion', () => {
    const file = new File(['data'], 'report.avif', { type: 'image/avif' })
    expect(needsModelImageTranscode(file)).toBe(true)
  })

  it('leaves jpeg unchanged', () => {
    const file = new File(['data'], 'report.jpg', { type: 'image/jpeg' })
    expect(needsModelImageTranscode(file)).toBe(false)
  })
})
