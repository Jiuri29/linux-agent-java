<template>
  <el-row :gutter="16">
    <el-col v-for="g in gauges" :key="g.label" :span="6">
      <el-card shadow="hover" class="gauge-card">
        <div class="gauge-inner">
          <el-progress
            type="dashboard"
            :percentage="g.percent"
            :width="130"
            :stroke-width="12"
            :color="g.color"
          >
            <template #default>
              <div class="gauge-value">
                <div class="num">{{ g.percent === null ? '--' : g.percent + '%' }}</div>
                <div class="sub">{{ g.sub }}</div>
              </div>
            </template>
          </el-progress>
          <div class="gauge-label">{{ g.label }}</div>
          <div class="gauge-detail">{{ g.detail }}</div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ snapshot: Record<string, any> | null }>()

function num(v: unknown): number | null {
  return typeof v === 'number' && Number.isFinite(v) ? v : null
}

function fmtKb(kb: unknown): string {
  const v = Number(kb)
  if (!Number.isFinite(v) || v <= 0) return '-'
  const gb = v / 1024 / 1024
  if (gb >= 1) return gb.toFixed(1) + ' GB'
  return (v / 1024).toFixed(0) + ' MB'
}

function levelColor(p: number | null): string | Array<any> {
  if (p === null) return '#dcdfe6'
  return [
    { color: '#67c23a', percentage: 60 },
    { color: '#e6a23c', percentage: 85 },
    { color: '#f56c6c', percentage: 100 },
  ]
}

const gauges = computed(() => {
  const s = props.snapshot
  const cpu = num(s?.cpu?.usage_percent)
  const mem = num(s?.memory?.usage_percent)
  const disks: any[] = Array.isArray(s?.disks) ? s.disks : []
  const maxDisk = disks.reduce((acc, d) => Math.max(acc, num(d.usage_percent) ?? 0), 0)
  const diskPct = disks.length ? Math.round(maxDisk * 10) / 10 : null
  const topDisk = disks.length
    ? disks.reduce((a, b) => ((num(b.usage_percent) ?? 0) > (num(a.usage_percent) ?? 0) ? b : a))
    : null
  const load1 = num(s?.load?.load1)
  const cores = num(s?.cpu?.logical_cores) ?? 1
  const loadPct = load1 === null ? null : Math.round(Math.min(100, (load1 / cores) * 100) * 10) / 10

  return [
    {
      label: 'CPU 使用率',
      percent: cpu,
      sub: cpu === null ? '采集中' : cpu + '%',
      detail: s?.cpu?.logical_cores ? `${s.cpu.logical_cores} 逻辑核` : '',
      color: levelColor(cpu),
    },
    {
      label: '内存使用率',
      percent: mem,
      sub: mem === null ? '无数据' : mem + '%',
      detail:
        s?.memory?.total_kb != null
          ? `${fmtKb(s.memory.total_kb - (s.memory.available_kb ?? 0))} / ${fmtKb(s.memory.total_kb)}`
          : '',
      color: levelColor(mem),
    },
    {
      label: '磁盘使用率（峰值）',
      percent: diskPct,
      sub: diskPct === null ? '无数据' : diskPct + '%',
      detail: topDisk ? String(topDisk.mounted_on ?? '') : '',
      color: levelColor(diskPct),
    },
    {
      label: '系统负载',
      percent: loadPct,
      sub: load1 === null ? '无数据' : load1.toFixed(2),
      detail: load1 === null ? '' : `load1 / ${cores} 核`,
      color: levelColor(loadPct),
    },
  ]
})
</script>

<style scoped>
.gauge-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.gauge-value .num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.gauge-value .sub {
  font-size: 12px;
  color: #909399;
}
.gauge-label {
  margin-top: 10px;
  font-weight: 600;
  font-size: 14px;
}
.gauge-detail {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
