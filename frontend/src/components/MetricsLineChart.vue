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
  const dark = document.documentElement.classList.contains('dark')
  const axisColor = dark ? '#6e7a91' : '#8a94a6'
  const splitColor = dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.06)'
  const tooltipBg = dark ? '#1c2330' : '#ffffff'
  const sorted = [...props.entries].sort((a, b) => String(a.ts).localeCompare(String(b.ts)))
  const xData = sorted.map((e) => String(e.ts ?? '').replace('T', ' ').slice(5, 19))
  const series = SERIES_DEF.map((def) => ({
    name: def.name,
    type: 'line' as const,
    smooth: true,
    showSymbol: false,
    connectNulls: true,
    itemStyle: { color: def.color },
    lineStyle: { width: 2.2 },
    areaStyle: {
      opacity: 1,
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: def.color + '3d' },
        { offset: 1, color: def.color + '05' },
      ]),
    },
    emphasis: { focus: 'series' as const },
    data: sorted.map((e) => {
      const m = e.metrics ?? e
      const v = Number(m[def.key])
      return Number.isFinite(v) ? v : null
    }),
  }))
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: splitColor,
      textStyle: { color: dark ? '#e6edf3' : '#303133', fontSize: 12 },
      valueFormatter: (v: unknown) => (v == null ? '-' : v + '%'),
    },
    legend: { data: SERIES_DEF.map((s) => s.name), top: 0, icon: 'roundRect', itemWidth: 14, itemHeight: 4 },
    grid: { left: 44, right: 18, top: 34, bottom: 28 },
    xAxis: {
      type: 'category',
      data: xData,
      boundaryGap: false,
      axisLine: { lineStyle: { color: splitColor } },
      axisLabel: { color: axisColor, fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { color: axisColor, fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: splitColor } },
    },
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
  themeObserver = new MutationObserver(render)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

let themeObserver: MutationObserver | undefined

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(() => props.entries, render, { deep: true })
</script>
