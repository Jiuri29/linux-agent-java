<template>
  <div ref="chartEl" :style="{ height: height, width: '100%' }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    entries: Record<string, any>[]
    height?: string
  }>(),
  { height: '360px' },
)

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const SERIES_DEF = [
  { key: 'cpu.usage_percent', name: 'CPU %', color: '#409eff' },
  { key: 'memory.usage_percent', name: '内存 %', color: '#67c23a' },
  { key: 'disk.usage_percent_max', name: '磁盘 %', color: '#e6a23c' },
]

function render() {
  if (!chart) return
  const sorted = [...props.entries].sort((a, b) => String(a.ts).localeCompare(String(b.ts)))
  const xData = sorted.map((e) => String(e.ts ?? '').replace('T', ' ').slice(5, 19))
  const series = SERIES_DEF.map((def) => ({
    name: def.name,
    type: 'line' as const,
    smooth: true,
    showSymbol: false,
    connectNulls: true,
    itemStyle: { color: def.color },
    lineStyle: { width: 2 },
    areaStyle: { opacity: 0.08 },
    data: sorted.map((e) => {
      const m = e.metrics ?? e
      const v = Number(m[def.key])
      return Number.isFinite(v) ? v : null
    }),
  }))
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: SERIES_DEF.map((s) => s.name), top: 0 },
    grid: { left: 42, right: 16, top: 32, bottom: 28 },
    xAxis: { type: 'category', data: xData, boundaryGap: false },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series,
  }, true)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  chart = echarts.init(chartEl.value!)
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(() => props.entries, render, { deep: true })
</script>
