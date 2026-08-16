<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalFeatureNavBar title="饮食推荐" back-to="/home" />
    <ClinicalPageHeader
      eyebrow="饮食计划"
      title="饮食推荐"
      description="基于身体参数、用药情况、过敏与中药忌口，自动生成今日三餐方案。"
    >
      <div class="flex flex-wrap gap-2">
        <Button variant="secondary" @click="goCamera">
          <iconify-icon icon="solar:camera-outline" width="16" height="16" />
          拍照识热量
        </Button>
        <Button variant="secondary" @click="goPreferences">
          <iconify-icon icon="solar:settings-outline" width="16" height="16" />
          偏好设置
        </Button>
        <Button variant="secondary" @click="goFoods">
          <iconify-icon icon="solar:salad-outline" width="16" height="16" />
          食材库
        </Button>
      </div>
    </ClinicalPageHeader>

    <!-- 忌口告警 -->
    <ClinicalStateNotice
      v-if="plan?.taboos.length"
      tone="info"
      :title="`检测到 ${plan.taboos.length} 项忌口`"
      :description="plan.taboos.join('、')"
    />

    <!-- 加载中提示 -->
    <ClinicalStateNotice
      v-if="loading && !plan"
      tone="loading"
      title="正在生成饮食计划"
      description="根据你的身体参数与用药情况智能匹配中..."
    />

    <!-- 今日推荐概览 -->
    <ClinicalSurfaceCard
      v-if="plan"
      eyebrow="今日方案"
      title="今日推荐"
      :description="`目标热量 ${plan.targetCalories} kcal · ${today}`"
    >
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <ClinicalStatCard
          label="目标热量"
          :value="`${plan.targetCalories}`"
          hint="kcal / 天"
          icon="solar:fire-outline"
          tone="info"
        />
        <ClinicalStatCard
          label="蛋白质"
          :value="totalProtein.toFixed(1)"
          hint="克 / 天"
          icon="solar:bone-outline"
        />
        <ClinicalStatCard
          label="脂肪"
          :value="totalFat.toFixed(1)"
          hint="克 / 天"
          icon="solar:water-outline"
        />
        <ClinicalStatCard
          label="碳水"
          :value="totalCarb.toFixed(1)"
          hint="克 / 天"
          icon="solar:wheat-outline"
        />
      </div>
    </ClinicalSurfaceCard>

    <!-- 各餐次详情 -->
    <ClinicalSurfaceCard
      v-for="meal in plan?.meals"
      :key="meal.mealType"
      eyebrow="餐次"
      :title="mealTypeLabel(meal.mealType)"
    >
      <div class="space-y-2">
        <div
          v-for="item in meal.items"
          :key="item.foodId"
          class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <iconify-icon icon="solar:plate-outline" width="18" height="18" class="text-slate-500" />
              <span class="text-sm font-semibold text-slate-950">{{ item.foodName }}</span>
              <span class="text-xs text-slate-500">{{ item.quantityG }} 克</span>
            </div>
            <span class="text-sm font-semibold text-slate-900">{{ item.calories }} kcal</span>
          </div>
          <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
            <span>P {{ item.proteinG.toFixed(1) }}g</span>
            <span>F {{ item.fatG.toFixed(1) }}g</span>
            <span>C {{ item.carbG.toFixed(1) }}g</span>
          </div>
        </div>
      </div>

      <template #headerRight>
        <span class="text-sm font-semibold text-slate-900">目标 {{ meal.targetCalories }} kcal</span>
      </template>
    </ClinicalSurfaceCard>

    <!-- 操作区 -->
    <div v-if="plan" class="flex justify-center">
      <Button :loading="loading" @click="reload">
        <iconify-icon icon="solar:refresh-circle-outline" width="16" height="16" />
        重新生成
      </Button>
    </div>

    <!-- 空状态 -->
    <ClinicalStateNotice
      v-if="!plan && !loading"
      tone="empty"
      title="暂无推荐"
      description="点击下方按钮立即生成今日饮食计划。"
      action-label="生成计划"
      @action="reload"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import Button from '@/shared/components/ui/Button.vue'
import { useToast } from '@/composables/useToast'
import { generateDietPlan, type DietPlanResponse, type DietMeal } from '@/api/modules/diet'
import { getProfileSettings } from '@/api/modules/profile'

const router = useRouter()
const { error } = useToast()

const loading = ref(false)
const plan = ref<DietPlanResponse | null>(null)
const today = new Date().toISOString().slice(0, 10)

const totalProtein = computed(() =>
  plan.value?.targetProteinG ?? plan.value?.meals.reduce((sum, m) => sum + m.items.reduce((mealSum, item) => mealSum + item.proteinG, 0), 0) ?? 0,
)
const totalFat = computed(() =>
  plan.value?.targetFatG ?? plan.value?.meals.reduce((sum, m) => sum + m.items.reduce((mealSum, item) => mealSum + item.fatG, 0), 0) ?? 0,
)
const totalCarb = computed(() =>
  plan.value?.targetCarbG ?? plan.value?.meals.reduce((sum, m) => sum + m.items.reduce((mealSum, item) => mealSum + item.carbG, 0), 0) ?? 0,
)

function mealTypeLabel(mealType: DietMeal['mealType']): string {
  switch (mealType) {
    case 'breakfast':
      return '早餐'
    case 'lunch':
      return '午餐'
    case 'dinner':
      return '晚餐'
    default:
      return mealType
  }
}

async function reload() {
  loading.value = true
  try {
    const profile = await getProfileSettings()
    if (profile.height <= 0 || profile.weight <= 0 || profile.age <= 0) {
      throw new Error('请先完善个人资料中的年龄、身高和体重，再生成饮食计划。')
    }
    plan.value = await generateDietPlan({
      height: profile.height,
      weight: profile.weight,
      age: profile.age,
      gender: profile.gender,
      goal: 'maintenance',
      activityLevel: 'sedentary',
      dailyMealCount: 3,
    })
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '生成饮食计划失败'
    error('生成失败', msg)
  } finally {
    loading.value = false
  }
}

function goPreferences() {
  router.push('/diet/preferences')
}

function goFoods() {
  router.push('/diet/foods')
}

function goCamera() {
  router.push('/diet/camera')
}

onMounted(() => {
  void reload()
})
</script>
