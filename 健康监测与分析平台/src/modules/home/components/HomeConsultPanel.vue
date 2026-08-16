<template>
  <ClinicalSurfaceCard title="健康问询">
    <textarea
      :value="question"
      rows="4"
      class="w-full resize-none rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3 text-sm leading-6 text-slate-900 outline-none transition focus:border-[color:var(--accent-strong)]"
      placeholder="例如：下午总是疲劳怎么办？"
      @input="$emit('update:question', ($event.target as HTMLTextAreaElement).value)"
    />

    <div class="mt-3 flex flex-wrap gap-2">
      <button
        v-for="chip in chips"
        :key="chip"
        type="button"
        class="rounded-full border border-[color:var(--surface-border)] bg-transparent px-3 py-1 text-xs text-slate-700 transition hover:bg-white active:scale-[0.98]"
        @click="$emit('pick-chip', chip)"
      >
        {{ chip }}
      </button>
    </div>

    <div class="mt-4 flex justify-end">
      <button
        type="button"
        class="inline-flex items-center rounded-lg bg-[color:var(--accent-strong)] px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="loading || !question.trim()"
        :aria-busy="loading"
        @click="$emit('submit')"
      >
        <iconify-icon icon="solar:chat-round-line-outline" width="16" height="16" class="mr-1" />
        {{ loading ? '生成中…' : '获取建议' }}
      </button>
    </div>

    <div
      v-if="response"
      class="mt-4 rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
    >
      <p class="text-sm font-semibold text-slate-950">问询结果</p>
      <p class="mt-2 text-sm leading-6 text-slate-700">{{ response.answer }}</p>

      <div v-if="response.suggestions.length" class="mt-4 space-y-2">
        <p class="text-xs font-medium text-slate-500">建议</p>
        <ul class="space-y-2">
          <li
            v-for="suggestion in response.suggestions"
            :key="suggestion"
            class="flex items-start gap-2 rounded-2xl bg-white px-3 py-2 text-sm leading-6 text-slate-700"
          >
            <iconify-icon icon="solar:check-circle-outline" width="16" height="16" class="mt-1 text-emerald-700" />
            <span>{{ suggestion }}</span>
          </li>
        </ul>
      </div>

      <p class="mt-3 text-[11px] leading-5 text-slate-500">{{ response.disclaimer }}</p>
    </div>
  </ClinicalSurfaceCard>
</template>

<script setup lang="ts">
import type { ConsultResponse } from '@/api/modules/consult'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'

defineProps<{
  question: string
  chips: string[]
  loading: boolean
  response: ConsultResponse | null
}>()

defineEmits<{
  (e: 'update:question', value: string): void
  (e: 'pick-chip', chip: string): void
  (e: 'submit'): void
}>()
</script>
