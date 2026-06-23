<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="Data Export"
      title="导出数据"
      description="按时间范围和数据类型导出健康记录，兼顾归档、打印和二次分析场景。"
      :meta="formatLabel"
      meta-label="导出格式"
    >
      <Button variant="secondary" @click="goBack">
        <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
        返回
      </Button>
    </ClinicalPageHeader>

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <ClinicalStatCard label="导出范围" :value="dateRangeLabel" hint="可自定义或快速填充最近 30 天" icon="solar:calendar-outline" tone="default" />
      <ClinicalStatCard label="数据类型" :value="`${selectedTypeCount} 类`" hint="至少选择一种数据类型" icon="solar:documents-outline" tone="info" />
      <ClinicalStatCard label="任务状态" :value="statusLabel" :hint="statusHint" icon="solar:download-outline" tone="success" />
    </section>

    <div class="grid gap-4 xl:grid-cols-[0.95fr_1.05fr]">
      <ClinicalSurfaceCard
        eyebrow="Export Scope"
        title="导出范围"
        description="选择起止日期和输出格式，决定后续文件结构。"
      >
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-xs text-slate-500">开始日期</span>
            <input v-model="startDate" type="date" class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]" />
          </label>
          <label class="block">
            <span class="text-xs text-slate-500">结束日期</span>
            <input v-model="endDate" type="date" class="mt-1 w-full rounded-[1rem] border border-[color:var(--surface-border)] px-3 py-2.5 text-sm outline-none focus:border-[color:var(--ring)]" />
          </label>
        </div>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <button
            v-for="fmt in formats"
            :key="fmt.value"
            type="button"
            class="rounded-[1.2rem] border px-4 py-4 text-left transition"
            :class="format === fmt.value ? 'border-teal-300 bg-teal-50' : 'border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] hover:border-teal-200'"
            @click="format = fmt.value"
          >
            <p class="font-semibold text-slate-950">{{ fmt.label }}</p>
            <p class="mt-1 text-sm leading-6 text-slate-600">{{ fmt.desc }}</p>
          </button>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard
        eyebrow="Data Groups"
        title="数据类型"
        description="按需导出监测数据、上传资料、康复记录和提醒事件。"
      >
        <div class="space-y-3">
          <label
            v-for="item in typeOptions"
            :key="item.key"
            class="flex items-start gap-3 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
          >
            <input v-model="types[item.key]" type="checkbox" class="mt-1 h-4 w-4" />
            <div>
              <p class="text-sm font-semibold text-slate-950">{{ item.label }}</p>
              <p class="mt-1 text-sm leading-6 text-slate-600">{{ item.desc }}</p>
            </div>
          </label>
        </div>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalSurfaceCard
      eyebrow="Export Progress"
      :title="status === 'done' ? '导出完成' : '导出进度'"
      description="导出任务采用异步方式执行，适合 Beta 阶段做性能观测。"
    >
      <div class="rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4">
        <div class="mb-2 flex items-center justify-between text-sm">
          <span class="text-slate-600">当前进度</span>
          <Badge>{{ progress }}%</Badge>
        </div>
        <div class="h-2 rounded-full bg-white">
          <div class="h-full rounded-full bg-teal-600 transition-all" :style="{ width: `${progress}%` }" />
        </div>
        <p class="mt-3 text-sm leading-6 text-slate-600">{{ statusHint }}</p>
      </div>
    </ClinicalSurfaceCard>

    <div class="grid grid-cols-2 gap-2.5">
      <Button variant="secondary" :disabled="status === 'exporting'" @click="fillRecent30Days">最近 30 天</Button>
      <Button :loading="status === 'exporting'" @click="handleExport">开始导出</Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'

type ExportStatus = 'idle' | 'exporting' | 'done'
type ExportTypeKey = 'monitor' | 'upload' | 'rehab' | 'alert'

const router = useRouter()
const { success, warning, info } = useToast()

const today = new Date()
const startDate = ref('')
const endDate = ref('')

const format = ref<'csv' | 'pdf'>('csv')
const formats = [
  { value: 'csv', label: 'CSV 数据表', desc: '适合表格分析与二次处理' },
  { value: 'pdf', label: 'PDF 报告', desc: '适合归档与打印携带' },
] as const

const typeOptions: { key: ExportTypeKey; label: string; desc: string }[] = [
  { key: 'monitor', label: '监测数据', desc: '心率、睡眠、压力等趋势记录' },
  { key: 'upload', label: '上传资料', desc: '影像、报告与文字说明' },
  { key: 'rehab', label: '康复记录', desc: '计划完成度与训练日志' },
  { key: 'alert', label: '提醒与事件', desc: '异常提醒与关键时间点' },
]

const types = reactive<Record<ExportTypeKey, boolean>>({
  monitor: true,
  upload: true,
  rehab: true,
  alert: false,
})

const selectedTypeCount = computed(() => Object.values(types).filter(Boolean).length)
const formatLabel = computed(() => formats.find((item) => item.value === format.value)?.label ?? 'CSV 数据表')
const dateRangeLabel = computed(() => {
  if (!startDate.value || !endDate.value) return '未设置'
  return `${startDate.value} 至 ${endDate.value}`
})

const status = ref<ExportStatus>('idle')
const progress = ref(0)
let timer: number | null = null

const statusHint = computed(() => {
  if (status.value === 'done') return '文件已生成，可在下载中心查看。'
  if (progress.value < 40) return '正在整理基础数据结构…'
  if (progress.value < 75) return '正在汇总趋势与关键指标…'
  return '正在打包文件与生成索引…'
})
const statusLabel = computed(() => {
  if (status.value === 'exporting') return '导出中'
  if (status.value === 'done') return '已完成'
  return '待开始'
})

const formatDate = (date: Date) => date.toISOString().slice(0, 10)

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}

const fillRecent30Days = () => {
  const end = new Date(today)
  const start = new Date(today)
  start.setDate(start.getDate() - 29)
  startDate.value = formatDate(start)
  endDate.value = formatDate(end)
  info('已填充最近 30 天', '你可以继续调整时间范围。')
}

fillRecent30Days()

const clearTimer = () => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

const handleExport = () => {
  if (!startDate.value || !endDate.value) {
    warning('请选择时间范围', '开始与结束日期都需要填写。')
    return
  }
  if (selectedTypeCount.value === 0) {
    warning('请选择至少一种数据类型', '否则没有可导出的内容。')
    return
  }

  clearTimer()
  status.value = 'exporting'
  progress.value = 6

  timer = window.setInterval(() => {
    const step = Math.round(6 + Math.random() * 12)
    progress.value = Math.min(100, progress.value + step)
    if (progress.value >= 100) {
      clearTimer()
      status.value = 'done'
      success('导出任务完成', `${formatLabel.value} 已准备就绪。`)
    }
  }, 420)
}
</script>
