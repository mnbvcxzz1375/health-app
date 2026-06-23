export type ExerciseLevel = '基础' | '进阶'

export type RehabExercise = {
  id: number
  name: string
  category: string
  duration: string
  level: ExerciseLevel
  minutes: number
  steps: string[]
  caution: string
  focus: string
  benefits: string[]
  videoMinutes: number
}

export const rehabExercises: RehabExercise[] = [
  {
    id: 1,
    name: '鸟狗式',
    category: '核心稳定',
    duration: '3 组 × 12 次',
    level: '基础',
    minutes: 8,
    steps: ['保持脊柱中立位', '对侧手脚伸直，避免塌腰', '动作慢、控制回位'],
    caution: '腰部出现明显疼痛请停止，必要时减少幅度。',
    focus: '核心稳定与抗旋转控制',
    benefits: ['提升腰背稳定性', '改善动作控制', '降低代偿风险'],
    videoMinutes: 6,
  },
  {
    id: 2,
    name: '死虫式',
    category: '核心稳定',
    duration: '3 组 × 10 次',
    level: '基础',
    minutes: 8,
    steps: ['腰背贴地，保持腹压', '对侧手脚缓慢伸展', '呼气时伸展，吸气回位'],
    caution: '注意不要憋气；如颈部紧张可垫枕头。',
    focus: '核心抗伸展控制',
    benefits: ['增强腹部控制', '改善骨盆稳定', '缓解下背部压力'],
    videoMinutes: 5,
  },
  {
    id: 3,
    name: '髂腰肌拉伸',
    category: '灵活性',
    duration: '每侧 2 组 × 30 秒',
    level: '基础',
    minutes: 6,
    steps: ['跪姿弓步，骨盆微后倾', '收紧臀部，感受前侧拉伸', '左右两侧均匀进行'],
    caution: '膝盖不适可在下方垫软垫。',
    focus: '髋屈肌放松与骨盆位置调整',
    benefits: ['改善久坐紧张', '提升髋部活动度', '辅助腰背舒展'],
    videoMinutes: 4,
  },
  {
    id: 4,
    name: '弹力带划船',
    category: '上背激活',
    duration: '3 组 × 12 次',
    level: '进阶',
    minutes: 10,
    steps: ['肩胛先后收再下压', '肘部贴近身体向后拉', '全程保持胸椎稳定'],
    caution: '肩部疼痛或不适请减少阻力或暂停。',
    focus: '肩胛稳定与上背激活',
    benefits: ['改善含胸圆肩', '增强上背耐力', '提高姿势支撑'],
    videoMinutes: 7,
  },
]

export function getExerciseByName(name?: string) {
  if (!name) return rehabExercises[0]
  const target = rehabExercises.find(item => item.name === name)
  return target ?? rehabExercises[0]
}
