import { http } from '@/api/http'
import { withMockFallback } from '@/dev/mockApi'

/** 后端 food_items 表的营养字段。 */
export type FoodSearchItem = {
  id: number
  name: string
  category: string
  caloriesPer100g: number
  proteinG: number
  fatG: number
  carbG: number
  fiberG: number
  sodiumMg: number
  potassiumMg: number
  glycemicIndex: number | null
  tags: string[]
}

/** 与后端 DietDtos.DietPlanRequest 一致。数值由用户画像提供，不允许页面静默发送空请求。 */
export type DietPlanRequest = {
  height: number
  weight: number
  age: number
  gender: 'male' | 'female' | 'other'
  goal: 'fat_loss' | 'muscle_gain' | 'maintenance'
  activityLevel: 'sedentary' | 'light' | 'moderate' | 'active'
  dailyMealCount?: number
}

export type DietMealItem = {
  foodId: number
  foodName: string
  category: string
  quantityG: number
  calories: number
  proteinG: number
  fatG: number
  carbG: number
}

export type DietMeal = {
  mealType: string
  targetCalories: number
  items: DietMealItem[]
}

/** 饮食计划响应，与后端 DietDtos.DietPlanResponse 一致。 */
export type DietPlanResponse = {
  bmi: number
  bmiCategory: string
  bmr: number
  tdee: number
  targetCalories: number
  targetProteinG: number
  targetFatG: number
  targetCarbG: number
  meals: DietMeal[]
  taboos: string[]
  warnings: string[]
}

export type DietPreference = {
  dietStyle: string
  dislikedFoods: string[]
  preferredCuisine: string
  dailyMealCount: number
  avoidSpicy: boolean
  avoidCold: boolean
  vegetarian: boolean
  updatedAt?: string
}

export type DietPreferenceSaveRequest = Omit<DietPreference, 'updatedAt'>

export type FoodRecognitionResponse = {
  foodName: string
  category: string
  confidence: number
  weightGrams: number
  portion: string
  per100g: {
    calories: number
    protein: number
    carbs: number
    fat: number
    fiber: number
    sodium: number
    potassium: number
  }
  calories: number
  protein: number
  carbs: number
  fat: number
  source: string
  warnings: string[]
}

export type DietLogEntry = {
  id: number
  foodName: string
  category: string
  weightGrams: number
  calories: number
  protein: number
  carbs: number
  fat: number
  source: string
  recordedAt: string
}

export type DietLogAuditEntry = {
  id: number
  dietLogId: number
  action: 'created' | 'updated' | 'deleted' | string
  beforeJson: string | null
  afterJson: string | null
  reason: string | null
  createdAt: string
}

export async function searchFoods(keyword: string, limit = 20): Promise<FoodSearchItem[]> {
  return withMockFallback(
    async () => {
      const resp = await http.get<FoodSearchItem[]>('/api/diet/foods/search', { params: { keyword, limit } })
      return resp.data
    },
    () => mockFoodSearch(keyword, limit),
  )
}

export async function generateDietPlan(req: DietPlanRequest): Promise<DietPlanResponse> {
  return withMockFallback(
    async () => {
      const resp = await http.post<DietPlanResponse>('/api/diet/plan', req)
      return resp.data
    },
    () => mockDietPlan(req),
  )
}

export async function getDietPreference(): Promise<DietPreference> {
  return withMockFallback(
    async () => {
      const resp = await http.get<DietPreference>('/api/diet/preferences')
      return resp.data
    },
    () => defaultPreference(),
  )
}

export async function saveDietPreference(pref: DietPreferenceSaveRequest): Promise<DietPreference> {
  return withMockFallback(
    async () => {
      const resp = await http.post<DietPreference>('/api/diet/preferences', pref)
      return resp.data
    },
    () => ({ ...pref, updatedAt: new Date().toISOString() }),
  )
}

/** 调用真实多模态食物识别；生产环境不再在前端随机生成营养结果。 */
export async function recognizeFood(file: File): Promise<FoodRecognitionResponse> {
  if (file.size <= 0) throw new Error('图片为空，请重新选择。')
  if (!file.type.startsWith('image/')) throw new Error('仅支持图片文件。')
  return withMockFallback(
    async () => {
      const formData = new FormData()
      formData.append('file', file)
      const resp = await http.post<FoodRecognitionResponse>('/api/diet/recognize', formData, { timeout: 180_000 })
      return resp.data
    },
    () => {
      const food = mockFoodSearch('', 1)[0]
      const weightGrams = 200
      return {
        foodName: food.name,
        category: food.category,
        confidence: 82,
        weightGrams,
        portion: '开发模式示例分量（约 200g）',
        per100g: {
          calories: food.caloriesPer100g,
          protein: food.proteinG,
          carbs: food.carbG,
          fat: food.fatG,
          fiber: food.fiberG,
          sodium: food.sodiumMg,
          potassium: food.potassiumMg,
        },
        calories: Math.round(food.caloriesPer100g * 2),
        protein: Math.round(food.proteinG * 2 * 10) / 10,
        carbs: Math.round(food.carbG * 2 * 10) / 10,
        fat: Math.round(food.fatG * 2 * 10) / 10,
        source: 'dev_mock',
        warnings: ['开发模式示例数据，不代表真实识别结果。'],
      }
    },
  )
}

export async function getTodayDietLogs(): Promise<DietLogEntry[]> {
  const resp = await http.get<DietLogEntry[]>('/api/diet/logs/today')
  return resp.data
}

export async function saveDietLog(entry: Omit<DietLogEntry, 'id' | 'recordedAt'> & { recordedAt?: string }): Promise<DietLogEntry> {
  const resp = await http.post<DietLogEntry>('/api/diet/logs', entry)
  return resp.data
}

export async function updateDietLog(
  id: number,
  entry: Omit<DietLogEntry, 'id' | 'recordedAt'> & { recordedAt?: string },
): Promise<DietLogEntry> {
  const resp = await http.put<DietLogEntry>(`/api/diet/logs/${id}`, entry)
  return resp.data
}

export async function deleteDietLog(id: number): Promise<{ success: boolean; dietLogId: number; message: string }> {
  const resp = await http.delete<{ success: boolean; dietLogId: number; message: string }>(`/api/diet/logs/${id}`)
  return resp.data
}

export async function getDietLogAudit(id: number): Promise<DietLogAuditEntry[]> {
  const resp = await http.get<DietLogAuditEntry[]>(`/api/diet/logs/${id}/audit`)
  return resp.data
}

function defaultPreference(): DietPreference {
  return {
    dietStyle: 'balanced',
    dislikedFoods: [],
    preferredCuisine: '',
    dailyMealCount: 3,
    avoidSpicy: false,
    avoidCold: false,
    vegetarian: false,
  }
}

function mockFoodSearch(keyword: string, limit: number): FoodSearchItem[] {
  const all: FoodSearchItem[] = [
    { id: 1, name: '燕麦', category: '谷物', caloriesPer100g: 389, proteinG: 17, fatG: 7, carbG: 67, fiberG: 10, sodiumMg: 2, potassiumMg: 429, glycemicIndex: 55, tags: ['高纤', '低GI'] },
    { id: 2, name: '糙米', category: '谷物', caloriesPer100g: 348, proteinG: 8, fatG: 3, carbG: 73, fiberG: 4, sodiumMg: 5, potassiumMg: 240, glycemicIndex: 50, tags: ['高纤'] },
    { id: 3, name: '鸡胸肉', category: '肉类', caloriesPer100g: 165, proteinG: 31, fatG: 4, carbG: 0, fiberG: 0, sodiumMg: 74, potassiumMg: 256, glycemicIndex: 0, tags: ['高蛋白'] },
    { id: 4, name: '三文鱼', category: '肉类', caloriesPer100g: 208, proteinG: 20, fatG: 13, carbG: 0, fiberG: 0, sodiumMg: 59, potassiumMg: 363, glycemicIndex: 0, tags: ['高蛋白', 'Omega-3'] },
    { id: 5, name: '西兰花', category: '蔬菜', caloriesPer100g: 34, proteinG: 3, fatG: 0, carbG: 7, fiberG: 3, sodiumMg: 33, potassiumMg: 316, glycemicIndex: 15, tags: ['低卡', '高纤'] },
    { id: 6, name: '菠菜', category: '蔬菜', caloriesPer100g: 23, proteinG: 3, fatG: 0, carbG: 4, fiberG: 2, sodiumMg: 79, potassiumMg: 558, glycemicIndex: 15, tags: ['高铁'] },
  ]
  const k = keyword.trim().toLowerCase()
  return (k ? all.filter((f) => f.name.includes(k) || f.category.includes(k) || f.tags.some((t) => t.includes(k))) : all).slice(0, limit)
}

function mockDietPlan(req: DietPlanRequest): DietPlanResponse {
  const bmr = Math.round(10 * req.weight + 6.25 * req.height - 5 * req.age + (req.gender === 'female' ? -161 : 5))
  const factor = req.activityLevel === 'active' ? 1.725 : req.activityLevel === 'moderate' ? 1.55 : req.activityLevel === 'light' ? 1.375 : 1.2
  const tdee = Math.round(bmr * factor)
  const targetCalories = req.goal === 'fat_loss' ? tdee - 500 : req.goal === 'muscle_gain' ? tdee + 300 : tdee
  const item = (foodId: number, foodName: string, category: string, quantityG: number, calories: number, proteinG: number, fatG: number, carbG: number): DietMealItem => ({ foodId, foodName, category, quantityG, calories, proteinG, fatG, carbG })
  const meals: DietMeal[] = [
    { mealType: 'breakfast', targetCalories: Math.round(targetCalories * 0.3), items: [item(1, '燕麦', '谷物', 50, 195, 8.5, 3.5, 33.5)] },
    { mealType: 'lunch', targetCalories: Math.round(targetCalories * 0.4), items: [item(3, '鸡胸肉', '肉类', 150, 248, 46.5, 6, 0), item(5, '西兰花', '蔬菜', 200, 68, 6, 0, 14)] },
    { mealType: 'dinner', targetCalories: Math.round(targetCalories * 0.3), items: [item(2, '糙米', '谷物', 100, 348, 8, 3, 73)] },
  ]
  return {
    bmi: Math.round((req.weight / (req.height / 100) ** 2) * 10) / 10,
    bmiCategory: 'normal',
    bmr,
    tdee,
    targetCalories,
    targetProteinG: Math.round(targetCalories * 0.25 / 4),
    targetFatG: Math.round(targetCalories * 0.25 / 9),
    targetCarbG: Math.round(targetCalories * 0.5 / 4),
    meals,
    taboos: [],
    warnings: ['开发模式示例数据，不代表真实个性化建议。'],
  }
}
