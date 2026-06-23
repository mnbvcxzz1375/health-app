<template>
  <!-- ECharts 容器，父容器需要给高度（例如 h-64） -->
  <div ref="el" class="h-full w-full" />
</template>

<script setup lang="ts">
/**
 * ECharts 轻量封装（不依赖 vue-echarts）
 *
 * 目的：
 * - 统一初始化/销毁逻辑
 * - 统一 resize 监听
 * - 页面里只关心 option
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsType, EChartsCoreOption } from 'echarts'

const props = defineProps<{
  option: EChartsCoreOption
}>()

const el = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null

const render = () => {
  if (!chart) return
  chart.setOption(props.option, { notMerge: true })
}

const resize = () => chart?.resize()

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', resize)
})

watch(
  () => props.option,
  () => render(),
  { deep: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>
