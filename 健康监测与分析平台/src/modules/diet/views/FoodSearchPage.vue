<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="食材库"
      title="食材库"
      description="检索食材的营养成分、升糖指数（GI）与标签，辅助搭配饮食计划。"
    >
      <button
        type="button"
        class="flex h-9 w-9 items-center justify-center rounded-full transition active:scale-95"
        style="background: var(--secondary); color: var(--foreground);"
        aria-label="返回"
        @click="goBack"
      >
        <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
      </button>
    </ClinicalPageHeader>

    <!-- 搜索 -->
    <ClinicalSurfaceCard
      eyebrow="搜索"
      title="搜索"
      description="输入食材名称、类别或标签，例如：燕麦、蔬菜、低GI。"
    >
      <input
        v-model="keyword"
        type="text"
        placeholder="如：燕麦 / 蔬菜 / 高蛋白"
        class="w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none focus:border-[color:var(--accent-strong)]"
        @input="onInput"
      />

      <div class="mt-4 flex flex-wrap gap-2">
        <button
          v-for="tag in quickTags"
          :key="tag"
          type="button"
          class="rounded-full border px-3 py-1.5 text-xs transition"
          :class="keyword === tag ? 'border-teal-300 bg-teal-50 text-teal-900' : 'border-[color:var(--surface-border)] bg-white text-slate-600 hover:text-slate-900'"
          @click="pickTag(tag)"
        >
          {{ tag }}
        </button>
      </div>
    </ClinicalSurfaceCard>

    <!-- 加载中提示 -->
    <ClinicalStateNotice
      v-if="loading && !results.length"
      tone="loading"
      title="正在加载食材"
      description="请稍候..."
    />

    <!-- 搜索结果 -->
    <div v-if="results.length" class="space-y-3">
      <ClinicalSurfaceCard
        v-for="food in results"
        :key="food.id"
        :eyebrow="food.category"
        :title="food.name"
        :description="`每 100g：${food.caloriesPer100g} kcal`"
      >
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-2 text-sm text-slate-700 sm:grid-cols-3">
            <p><span class="text-slate-400">蛋白质：</span>{{ food.proteinG }}g</p>
            <p><span class="text-slate-400">脂肪：</span>{{ food.fatG }}g</p>
            <p><span class="text-slate-400">碳水：</span>{{ food.carbG }}g</p>
            <p><span class="text-slate-400">纤维：</span>{{ food.fiberG }}g</p>
            <p><span class="text-slate-400">钠：</span>{{ food.sodiumMg }}mg</p>
            <p><span class="text-slate-400">钾：</span>{{ food.potassiumMg }}mg</p>
            <p><span class="text-slate-400">GI：</span>{{ food.glycemicIndex || '—' }}</p>
          </div>

          <div v-if="food.tags.length" class="flex flex-wrap gap-2">
            <span
              v-for="tag in food.tags"
              :key="tag"
              class="rounded-full border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-2.5 py-1 text-[11px] text-slate-600"
            >
              {{ tag }}
            </span>
          </div>
        </div>
      </ClinicalSurfaceCard>
    </div>

    <!-- 空状态 -->
    <ClinicalStateNotice
      v-else-if="!loading"
      tone="empty"
      title="未找到相关食材"
      description="可以更换关键词或点击快捷标签重试。"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import { useToast } from '@/composables/useToast'
import { searchFoods, type FoodSearchItem } from '@/api/modules/diet'

const router = useRouter()
const { error } = useToast()

const keyword = ref('')
const loading = ref(false)
const results = ref<FoodSearchItem[]>([])

const quickTags = ['谷物', '蔬菜', '水果', '肉类', '乳制品', '高蛋白', '低卡', '高纤', '低GI']

let debounceTimer: ReturnType<typeof setTimeout> | null = null

function onInput() {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    void doSearch()
  }, 300)
}

function pickTag(tag: string) {
  keyword.value = tag
  void doSearch()
}

async function doSearch() {
  loading.value = true
  try {
    results.value = await searchFoods(keyword.value.trim(), 20)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '搜索食材失败'
    error('搜索失败', msg)
    results.value = []
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/diet')
}

onMounted(() => {
  void doSearch()
})
</script>
