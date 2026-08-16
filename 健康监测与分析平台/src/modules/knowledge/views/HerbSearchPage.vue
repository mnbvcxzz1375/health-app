<template>
  <div class="space-y-5 pb-6 text-slate-950">
    <ClinicalFeatureNavBar title="中药材搜索" back-to="/knowledge" />

    <ClinicalPageHeader
      eyebrow="中药材"
      title="中药材搜索"
      description="输入药名、拼音或别名检索中药材的性味归经与功效。"
    />

    <ClinicalSurfaceCard title="搜索">
      <div class="flex gap-2">
        <input
          v-model="keyword"
          type="text"
          placeholder="如：人参 / renshen / 黄芪"
          class="flex-1 rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none focus:border-[color:var(--accent-strong)]"
          @keyup.enter="doSearch"
        />
        <Button :loading="loading" @click="doSearch">搜索</Button>
      </div>
    </ClinicalSurfaceCard>

    <ClinicalStateNotice
      v-if="viewState === 'error'"
      tone="error"
      title="搜索失败"
      :description="errorMsg || '请稍后重试'"
      action-label="重试"
      @action="doSearch"
    />

    <ClinicalStateNotice
      v-else-if="viewState === 'empty'"
      tone="empty"
      title="未找到匹配药材"
      description="请尝试其他关键词。"
    />

    <div v-else-if="results.length" class="space-y-3">
      <article
        v-for="herb in results"
        :key="herb.id"
        class="rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-4"
      >
        <div class="flex items-center gap-2">
          <iconify-icon icon="solar:leaf-outline" width="20" height="20" class="text-emerald-700" />
          <span class="text-base font-semibold">{{ herb.name }}</span>
          <span v-if="herb.pinyin" class="text-xs text-slate-500">{{ herb.pinyin }}</span>
          <span v-if="herb.alias" class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">别名：{{ herb.alias }}</span>
        </div>
        <div class="mt-3 grid grid-cols-2 gap-2 text-sm text-slate-700 sm:grid-cols-3">
          <p><span class="text-slate-400">性：</span>{{ herb.nature || '—' }}</p>
          <p><span class="text-slate-400">味：</span>{{ herb.flavor || '—' }}</p>
          <p><span class="text-slate-400">归经：</span>{{ herb.meridian || '—' }}</p>
        </div>
        <p class="mt-3 text-sm leading-6 text-slate-700">
          <span class="text-slate-400">功效：</span>{{ herb.efficacy || '—' }}
        </p>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import Button from '@/shared/components/ui/Button.vue'
import { searchHerbs, type HerbSearchItem } from '@/api/modules/knowledge'

const keyword = ref('')
const loading = ref(false)
const results = ref<HerbSearchItem[]>([])
const errorMsg = ref('')
const viewState = ref<'idle' | 'empty' | 'error' | 'success'>('idle')

async function doSearch() {
  if (!keyword.value.trim()) {
    results.value = []
    viewState.value = 'empty'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await searchHerbs(keyword.value.trim(), 30)
    results.value = data
    viewState.value = data.length ? 'success' : 'empty'
  } catch (e: any) {
    errorMsg.value = e?.message ?? '搜索失败'
    viewState.value = 'error'
  } finally {
    loading.value = false
  }
}
</script>
