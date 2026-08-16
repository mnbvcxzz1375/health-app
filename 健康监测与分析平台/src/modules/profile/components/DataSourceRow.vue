<template>
  <div
    class="rounded-[12px] border p-3 transition active:scale-[0.99]"
    style="background: var(--secondary); border-color: var(--border);"
  >
    <div class="flex items-start gap-3">
      <div
        class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full"
        :style="{ background: iconBg, color: iconColor }"
      >
        <iconify-icon :icon="typeIcon" width="16" height="16" />
      </div>

      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2">
          <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ source.sourceName }}</p>
          <span
            class="rounded-full border px-2 py-0.5 text-[11px]"
            :style="{ background: badgeBg, color: badgeColor, borderColor: badgeColor }"
          >{{ typeLabel }}</span>
        </div>

        <p class="mt-1 text-[13px] leading-5" style="color: var(--muted-foreground);">{{ source.citation }}</p>

        <div class="mt-2 flex flex-wrap items-center gap-3 text-[12px]" style="color: var(--muted-foreground);">
          <span class="inline-flex items-center gap-1">
            <iconify-icon icon="solar:documents-outline" width="12" height="12" />
            {{ source.recordCount }} 条记录
          </span>
          <span v-if="updatedAtLabel" class="inline-flex items-center gap-1">
            <iconify-icon icon="solar:calendar-outline" width="12" height="12" />
            {{ updatedAtLabel }}
          </span>
          <a
            v-if="source.referenceUrl"
            :href="source.referenceUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1 font-medium"
            style="color: var(--brand-500);"
            @click.stop
          >
            <iconify-icon icon="solar:link-circle-outline" width="12" height="12" />
            访问数据源
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DataSourceItem } from '@/api/modules/dataSources'

const props = defineProps<{
  source: DataSourceItem
}>()

const typeMeta = computed(() => {
  switch (props.source.sourceType) {
    case 'open':
      return {
        label: '开源',
        icon: 'solar:earth-outline',
        iconBg: 'var(--state-success-surface)',
        iconColor: 'var(--state-success)',
        badgeBg: 'color-mix(in srgb, var(--state-success) 12%, var(--card))',
        badgeColor: 'var(--state-success)',
      }
    case 'academic':
      return {
        label: '学术',
        icon: 'solar:book-outline',
        iconBg: 'var(--brand-50)',
        iconColor: 'var(--brand-500)',
        badgeBg: 'var(--brand-50)',
        badgeColor: 'var(--brand-500)',
      }
    case 'manual':
      return {
        label: '人工',
        icon: 'solar:hand-stars-outline',
        iconBg: 'color-mix(in srgb, var(--chart-3) 12%, var(--card))',
        iconColor: 'var(--chart-3)',
        badgeBg: 'color-mix(in srgb, var(--chart-3) 12%, var(--card))',
        badgeColor: 'var(--chart-3)',
      }
    case 'api':
    default:
      return {
        label: props.source.sourceType === 'api' ? 'API' : props.source.sourceType,
        icon: 'solar:server-outline',
        iconBg: 'var(--background-200)',
        iconColor: 'var(--foreground)',
        badgeBg: 'var(--background-200)',
        badgeColor: 'var(--foreground)',
      }
  }
})

const typeLabel = computed(() => typeMeta.value.label)
const typeIcon = computed(() => typeMeta.value.icon)
const iconBg = computed(() => typeMeta.value.iconBg)
const iconColor = computed(() => typeMeta.value.iconColor)
const badgeBg = computed(() => typeMeta.value.badgeBg)
const badgeColor = computed(() => typeMeta.value.badgeColor)

const updatedAtLabel = computed(() => {
  if (!props.source.lastUpdatedAt) return ''
  try {
    const d = new Date(props.source.lastUpdatedAt)
    return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  } catch {
    return props.source.lastUpdatedAt.slice(0, 10)
  }
})
</script>
