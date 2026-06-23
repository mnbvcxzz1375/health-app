import { normalizeRehabExercise } from '@/api/modules/rehab'

describe('rehab normalization', () => {
  it('translates english exercise content to chinese', () => {
    const exercise = normalizeRehabExercise({
      id: 1,
      name: 'Bird Dog',
      category: 'Core Stability',
      duration: '3 x 12',
      level: 'basic',
      minutes: 8,
      steps: ['Keep a neutral spine', 'Reach opposite arm and leg'],
      caution: 'Stop if you feel sharp low-back pain.',
      focus: 'Core stability and anti-rotation control',
      benefits: ['Improve trunk stability', 'Reduce compensation risk'],
      videoMinutes: 6,
      done: false,
    })

    expect(exercise.name).toBe('鸟狗式')
    expect(exercise.category).toBe('核心稳定')
    expect(exercise.duration).toBe('3 组 × 12 次')
    expect(exercise.level).toBe('基础')
    expect(exercise.steps).toEqual(['保持脊柱中立位', '对侧手脚伸直'])
    expect(exercise.focus).toBe('核心稳定与抗旋转控制')
    expect(exercise.benefits).toEqual(['提升躯干稳定性', '降低代偿风险'])
  })
})
