<template>
  <ProfileSubPage title="导出数据" subtitle="按时间范围和数据类型导出健康记录">
    <div class="space-y-5">
      <!-- 状态概览 -->
      <section
        class="rounded-[19.2px] border p-5"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="grid grid-cols-3 gap-3">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">导出范围</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ dateRangeLabel }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">数据类型</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ selectedTypeCount }} 类</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">任务状态</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ statusLabel }}</p>
          </div>
        </div>
      </section>

      <!-- 导出范围 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">导出范围</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">选择起止日期和输出格式。</p>
        <div class="mt-3 grid grid-cols-2 gap-3">
          <label class="block">
            <span class="text-[13px]" style="color: var(--muted-foreground);">开始日期</span>
            <input
              v-model="startDate"
              type="date"
              class="mt-1.5 h-12 w-full rounded-[12px] border px-3 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
              style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[13px]" style="color: var(--muted-foreground);">结束日期</span>
            <input
              v-model="endDate"
              type="date"
              class="mt-1.5 h-12 w-full rounded-[12px] border px-3 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
              style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
            />
          </label>
        </div>

        <div class="mt-4 grid grid-cols-2 gap-3">
          <button
            v-for="fmt in formats"
            :key="fmt.value"
            type="button"
            class="rounded-[12px] border px-4 py-4 text-left transition active:scale-[0.99]"
            :style="format === fmt.value
              ? { background: 'var(--brand-50)', borderColor: 'var(--brand-500)' }
              : { background: 'var(--secondary)', borderColor: 'var(--border)' }"
            @click="format = fmt.value"
          >
            <p
              class="text-[15px] font-semibold"
              :style="format === fmt.value ? { color: 'var(--brand-500)' } : { color: 'var(--foreground)' }"
            >{{ fmt.label }}</p>
            <p class="mt-1 text-[12px] leading-4" style="color: var(--muted-foreground);">{{ fmt.desc }}</p>
          </button>
        </div>
      </section>

      <!-- 数据类型 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">数据类型</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">至少选择一种可导出的内容。</p>
        <div class="mt-3 space-y-2">
          <label
            v-for="item in typeOptions"
            :key="item.key"
            class="flex items-start gap-3 rounded-[12px] border px-4 py-3"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <input v-model="types[item.key]" type="checkbox" class="mt-0.5 h-5 w-5 accent-[color:var(--brand-500)]" />
            <div>
              <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ item.label }}</p>
              <p class="mt-0.5 text-[12px] leading-4" style="color: var(--muted-foreground);">{{ item.desc }}</p>
            </div>
          </label>
        </div>
      </section>

      <!-- 导出进度 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">
          {{ status === 'done' ? '导出完成' : '导出进度' }}
        </h2>
        <div
          class="mt-3 rounded-[12px] border p-4"
          style="background: var(--secondary); border-color: var(--border);"
        >
          <div class="mb-2 flex items-center justify-between text-[13px]">
            <span style="color: var(--muted-foreground);">当前进度</span>
            <span class="font-semibold tabular-nums" style="color: var(--foreground);">{{ progress }}%</span>
          </div>
          <div class="w-full overflow-hidden rounded-full" style="height: 6px; background: var(--background-200);">
            <div
              class="h-full rounded-full transition-all"
              :style="{ width: `${progress}%`, background: 'var(--state-success)' }"
            />
          </div>
          <p class="mt-3 text-[13px] leading-5" style="color: var(--muted-foreground);">{{ statusHint }}</p>
        </div>
      </section>

      <!-- 操作按钮 -->
      <div class="flex gap-3">
        <button
          type="button"
          class="flex h-[48px] flex-1 items-center justify-center rounded-full text-[15px] font-medium transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--foreground);"
          :disabled="status === 'exporting'"
          @click="fillRecent30Days"
        >
          最近 30 天
        </button>
        <button
          type="button"
          class="flex h-[48px] flex-1 items-center justify-center rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
          style="background: var(--primary); color: var(--primary-foreground);"
          :disabled="status === 'exporting'"
          @click="handleExport"
        >
          {{ status === 'exporting' ? '导出中…' : '开始导出' }}
        </button>
      </div>
    </div>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useToast } from '@/composables/useToast'
import ProfileSubPage from '../components/ProfileSubPage.vue'

type ExportStatus = 'idle' | 'exporting' | 'done'
type ExportTypeKey = 'monitor' | 'upload' | 'rehab' | 'alert'

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
