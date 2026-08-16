<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="偏好设置"
      title="饮食偏好"
      description="设置口味、忌口与菜系偏好，将用于饮食计划的智能匹配。"
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

    <ClinicalStateNotice
      v-if="loading && !loaded"
      tone="loading"
      title="正在加载偏好"
      description="请稍候..."
    />

    <!-- 口味偏好 -->
    <ClinicalSurfaceCard
      v-if="loaded"
      eyebrow="口味"
      title="口味偏好"
      description="勾选符合你习惯的选项，生成的饮食计划会自动规避。"
    >
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
        <label
          class="flex cursor-pointer items-center gap-2 rounded-[1.1rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm"
          :class="pref.avoidSpicy ? 'ring-1 ring-[color:var(--accent-soft)]' : ''"
        >
          <input v-model="pref.avoidSpicy" type="checkbox" class="h-4 w-4 accent-[color:var(--accent-strong)]" />
          <span class="text-slate-900">忌辛辣</span>
        </label>
        <label
          class="flex cursor-pointer items-center gap-2 rounded-[1.1rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm"
          :class="pref.avoidCold ? 'ring-1 ring-[color:var(--accent-soft)]' : ''"
        >
          <input v-model="pref.avoidCold" type="checkbox" class="h-4 w-4 accent-[color:var(--accent-strong)]" />
          <span class="text-slate-900">忌生冷</span>
        </label>
        <label
          class="flex cursor-pointer items-center gap-2 rounded-[1.1rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm"
          :class="pref.vegetarian ? 'ring-1 ring-[color:var(--accent-soft)]' : ''"
        >
          <input v-model="pref.vegetarian" type="checkbox" class="h-4 w-4 accent-[color:var(--accent-strong)]" />
          <span class="text-slate-900">素食</span>
        </label>
      </div>
    </ClinicalSurfaceCard>

    <!-- 不喜欢的食材 -->
    <ClinicalSurfaceCard
      v-if="loaded"
      eyebrow="忌口"
      title="不喜欢的食材"
      description="添加后生成的饮食计划将尽量避免这些食材。"
    >
      <div class="space-y-3">
        <div v-if="pref.dislikedFoods.length" class="flex flex-wrap gap-2">
          <span
            v-for="(food, idx) in pref.dislikedFoods"
            :key="`dislike-${food}-${idx}`"
            class="inline-flex items-center gap-1 rounded-full border border-[color:var(--surface-border)] bg-white px-3 py-1.5 text-xs text-slate-800"
          >
            {{ food }}
            <button
              type="button"
              class="inline-flex h-4 w-4 items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              @click="removeDislike(idx)"
            >
              <iconify-icon icon="solar:close-circle-outline" width="14" height="14" />
            </button>
          </span>
        </div>
        <p v-else class="text-xs text-slate-500">尚未添加不喜欢的食材。</p>

        <div class="flex gap-2">
          <input
            v-model="dislikeInput"
            type="text"
            placeholder="如：香菜、芹菜"
            class="flex-1 rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none focus:border-[color:var(--accent-strong)]"
            @keyup.enter="addDislike"
          />
          <Button variant="secondary" @click="addDislike">添加</Button>
        </div>
      </div>
    </ClinicalSurfaceCard>

    <!-- 偏好菜系 -->
    <ClinicalSurfaceCard
      v-if="loaded"
      eyebrow="菜系"
      title="偏好菜系"
      description="添加你喜欢的菜系风味，例如粤菜、川菜、日料等。"
    >
      <div class="space-y-3">
        <input
          v-model="pref.preferredCuisine"
          type="text"
          placeholder="如：粤菜、川菜、日料"
          class="w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none focus:border-[color:var(--accent-strong)]"
        />
      </div>
    </ClinicalSurfaceCard>

    <div v-if="loaded" class="flex justify-center">
      <Button :loading="saving" @click="save">
        <iconify-icon icon="solar:diskette-outline" width="16" height="16" />
        保存
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import Button from '@/shared/components/ui/Button.vue'
import { useToast } from '@/composables/useToast'
import { getDietPreference, saveDietPreference, type DietPreference } from '@/api/modules/diet'

const router = useRouter()
const { success, error } = useToast()

const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)

const pref = reactive<DietPreference>({
  dietStyle: 'balanced',
  dislikedFoods: [],
  preferredCuisine: '',
  dailyMealCount: 3,
  avoidSpicy: false,
  avoidCold: false,
  vegetarian: false,
})

const dislikeInput = ref('')

function addDislike() {
  const v = dislikeInput.value.trim()
  if (!v) return
  if (pref.dislikedFoods.includes(v)) {
    dislikeInput.value = ''
    return
  }
  pref.dislikedFoods.push(v)
  dislikeInput.value = ''
}

function removeDislike(idx: number) {
  pref.dislikedFoods.splice(idx, 1)
}

async function loadPreference() {
  loading.value = true
  try {
    const data = await getDietPreference()
    pref.dislikedFoods = [...data.dislikedFoods]
    pref.dietStyle = data.dietStyle
    pref.preferredCuisine = data.preferredCuisine
    pref.dailyMealCount = data.dailyMealCount
    pref.avoidSpicy = data.avoidSpicy
    pref.avoidCold = data.avoidCold
    pref.vegetarian = data.vegetarian
    loaded.value = true
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '加载偏好失败'
    error('加载失败', msg)
    loaded.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await saveDietPreference({
      dietStyle: pref.dietStyle,
      dislikedFoods: [...pref.dislikedFoods],
      preferredCuisine: pref.preferredCuisine,
      dailyMealCount: pref.dailyMealCount,
      avoidSpicy: pref.avoidSpicy,
      avoidCold: pref.avoidCold,
      vegetarian: pref.vegetarian,
    })
    success('已保存', '饮食偏好已更新。')
    goBack()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '保存偏好失败'
    error('保存失败', msg)
  } finally {
    saving.value = false
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
  void loadPreference()
})
</script>
