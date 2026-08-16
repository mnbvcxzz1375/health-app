<template>
  <ProfileSubPage title="数据来源" subtitle="平台所用药材、药品与营养数据的来源与许可说明">
    <div class="space-y-5">
      <!-- 概览 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">数据规模</h2>
        <div v-if="summary" class="mt-3 grid grid-cols-2 gap-3">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">数据源总数</p>
            <p class="mt-1 text-[17px] font-semibold tabular-nums" style="color: var(--foreground);">{{ summary.totalSources }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">总记录数</p>
            <p class="mt-1 text-[17px] font-semibold tabular-nums" style="color: var(--foreground);">{{ summary.totalRecords }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">开源 / 学术 / 人工</p>
            <p class="mt-1 text-[15px] font-semibold tabular-nums" style="color: var(--foreground);">
              {{ summary.byType.open }} / {{ summary.byType.academic }} / {{ summary.byType.manual }}
            </p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">最近更新</p>
            <p class="mt-1 text-[15px] font-semibold" style="color: var(--foreground);">{{ lastUpdatedLabel }}</p>
          </div>
        </div>
        <div v-else class="mt-3 grid grid-cols-2 gap-3">
          <div v-for="i in 4" :key="i" class="rounded-[10px] p-3" style="background: var(--secondary);">
            <div class="h-3 w-16 rounded" style="background: var(--background-200);"></div>
            <div class="mt-2 h-5 w-10 rounded" style="background: var(--background-300);"></div>
          </div>
        </div>
      </section>

      <!-- 开源数据源 -->
      <section
        v-if="openSources.length"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">开源数据源</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">遵守原数据 License，可自由使用与共享。</p>
        <div class="mt-3 space-y-2">
          <DataSourceRow v-for="s in openSources" :key="s.id" :source="s" />
        </div>
      </section>

      <!-- 学术数据源 -->
      <section
        v-if="academicSources.length"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">学术数据源</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">学术论文发表的数据集，使用时请引用原文。</p>
        <div class="mt-3 space-y-2">
          <DataSourceRow v-for="s in academicSources" :key="s.id" :source="s" />
        </div>
      </section>

      <!-- 人工整理数据源 -->
      <section
        v-if="manualSources.length"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">人工整理数据源</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">基于公共医学常识、教材与临床指南整理。</p>
        <div class="mt-3 space-y-2">
          <DataSourceRow v-for="s in manualSources" :key="s.id" :source="s" />
        </div>
      </section>

      <!-- 合规说明 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">合规说明</h2>
        <ul class="mt-3 space-y-2 text-[13px] leading-5" style="color: var(--muted-foreground);">
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>所有数据均标注来源与更新时间，遵守原数据 License。</span>
          </li>
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>第三方付费库仅通过授权 API 使用，禁止爬虫。</span>
          </li>
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>版权数据仅引用必要样例，不全文复制。</span>
          </li>
          <li class="flex gap-2">
            <span style="color: var(--brand-500);">·</span>
            <span>开源数据遵循 CC-BY-SA / ODbL 等许可要求。</span>
          </li>
        </ul>
      </section>

      <!-- 空状态 -->
      <div
        v-if="!loading && !summary"
        class="rounded-[19.2px] border p-6 text-center"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div
          class="mx-auto flex h-12 w-12 items-center justify-center rounded-full"
          style="background: var(--background-200); color: var(--muted-foreground);"
        >
          <iconify-icon icon="solar:folder-open-outline" width="24" height="24" />
        </div>
        <p class="mt-3 text-[15px] font-medium" style="color: var(--foreground);">暂无数据来源信息</p>
        <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">后端可能未启动或数据源表为空。</p>
        <button
          type="button"
          class="mt-4 flex h-10 items-center gap-1.5 rounded-full px-5 text-[13px] font-medium transition active:scale-95"
          style="background: var(--secondary); color: var(--foreground); margin: 16px auto 0;"
          @click="loadData"
        >
          <iconify-icon icon="solar:refresh-outline" width="16" height="16" />
          重新加载
        </button>
      </div>
    </div>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDataSourceSummary, listDataSources, type DataSourceItem, type DataSourceSummary } from '@/api/modules/dataSources'
import DataSourceRow from '../components/DataSourceRow.vue'
import ProfileSubPage from '../components/ProfileSubPage.vue'

const loading = ref(false)
const sources = ref<DataSourceItem[]>([])
const summary = ref<DataSourceSummary | null>(null)

const openSources = computed(() => sources.value.filter((s) => s.sourceType === 'open'))
const academicSources = computed(() => sources.value.filter((s) => s.sourceType === 'academic'))
const manualSources = computed(() => sources.value.filter((s) => s.sourceType === 'manual'))

const lastUpdatedLabel = computed(() => {
  if (!summary.value?.lastUpdatedAt) return '—'
  try {
    const d = new Date(summary.value.lastUpdatedAt)
    return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  } catch {
    return summary.value.lastUpdatedAt.slice(0, 10)
  }
})

async function loadData() {
  loading.value = true
  try {
    const [list, s] = await Promise.all([listDataSources(), getDataSourceSummary()])
    sources.value = list
    summary.value = s
  } catch (e) {
    console.error('[DataSourcesPage] 加载失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadData()
})
</script>
