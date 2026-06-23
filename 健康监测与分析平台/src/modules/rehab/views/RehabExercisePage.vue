<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalFeatureNavBar title="动作详情" back-to="/rehab" />
    <ClinicalPageHeader
      eyebrow="动作详情"
      :title="exercise.name"
      :description="`${exercise.focus} / 建议时长 ${exercise.minutes} 分钟`"
      :meta="exercise.level"
      meta-label="动作难度"
    >
      <Button @click="goReminder">设置提醒</Button>
    </ClinicalPageHeader>

    <div v-if="exerciseMedia" class="grid gap-4 xl:grid-cols-[1fr_0.95fr]">
      <ClinicalSurfaceCard title="动作示范">
        <div class="overflow-hidden rounded-[1.35rem] border border-[color:var(--surface-border)]">
          <img :src="exerciseMedia.imageSrc" :alt="exerciseMedia.imageAlt" class="h-56 w-full object-cover" />
        </div>
        <div class="mt-4 flex flex-wrap gap-2">
          <Badge>{{ exercise.category }}</Badge>
          <Badge :variant="exercise.level === '基础' ? 'success' : 'warning'">{{ exercise.level }}</Badge>
          <Badge>{{ exercise.duration }}</Badge>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard title="动态示范">
        <div class="overflow-hidden rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)]">
          <img :src="exerciseMedia.videoSrc" :alt="exerciseMedia.videoAlt" class="h-56 w-full object-cover" />
        </div>
        <p class="mt-3 text-sm leading-6 text-slate-600">开始练习前，先完整看一遍当前动作的动态示范。</p>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalSurfaceCard v-else title="动作说明">
      <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
        <p class="text-sm leading-6 text-slate-700">
          当前动作由上传分析后的康复计划生成，暂时没有预置示范图和视频，按下方文字说明执行。
        </p>
        <div class="mt-4 flex flex-wrap gap-2">
          <Badge>{{ exercise.category }}</Badge>
          <Badge :variant="exercise.level === '基础' ? 'success' : 'warning'">{{ exercise.level }}</Badge>
          <Badge>{{ exercise.duration }}</Badge>
        </div>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="训练收益与注意事项">
      <div class="grid gap-3 sm:grid-cols-2">
        <div class="rounded-[1.15rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
          <p class="text-xs uppercase tracking-[0.12em] text-slate-500">训练收益</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <span
              v-for="benefit in exercise.benefits"
              :key="benefit"
              class="rounded-full bg-white px-3 py-1.5 text-xs text-slate-700"
            >
              {{ benefit }}
            </span>
          </div>
        </div>

        <div class="rounded-[1.15rem] border border-amber-200 bg-amber-50/90 px-4 py-4">
          <p class="text-xs uppercase tracking-[0.12em] text-amber-800/80">注意事项</p>
          <p class="mt-3 text-sm leading-6 text-amber-950">{{ exercise.caution }}</p>
        </div>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalSurfaceCard title="动作要点">
      <div class="grid gap-3 lg:grid-cols-3">
        <article
          v-for="step in exercise.steps"
          :key="step"
          class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
        >
          <p class="text-sm font-semibold text-slate-950">{{ step }}</p>
        </article>
      </div>
    </ClinicalSurfaceCard>

    <div class="grid grid-cols-2 gap-2.5">
      <Button variant="secondary" @click="goReminder">设置提醒</Button>
      <Button :disabled="done" @click="markDone">{{ done ? '已记录完成' : '记录完成' }}</Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRehabExerciseByName, type RehabExercise } from '@/api/modules/rehab'
import { getRehabExerciseMedia } from '@/modules/rehab/data/exerciseMedia'
import { useToast } from '@/composables/useToast'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'

const route = useRoute()
const router = useRouter()
const { success, warning } = useToast()

const name = computed(() => (typeof route.query.name === 'string' ? route.query.name : ''))

const exercise = ref<RehabExercise>({
  id: 0,
  name: '加载中',
  category: '核心稳定',
  duration: '--',
  level: '基础',
  minutes: 0,
  steps: [],
  caution: '暂无',
  focus: '暂无',
  benefits: [],
  videoMinutes: 0,
  done: false,
})

const done = ref(false)
const exerciseMedia = computed(() => getRehabExerciseMedia(exercise.value.name))

const loadExercise = async () => {
  try {
    exercise.value = await getRehabExerciseByName(name.value || '鸟狗式')
  } catch (err) {
    warning('加载失败', err instanceof Error ? err.message : '请稍后重试。')
  }
}

const markDone = () => {
  if (done.value) return
  done.value = true
  success('已记录完成', `${exercise.value.name} 已加入今日完成度。`)
}

const goReminder = () => {
  router.push('/rehab/reminder')
}

onMounted(() => {
  void loadExercise()
})
</script>
