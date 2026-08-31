<template>
  <div>
    <!-- 实时监测：主视觉区 -->
    <el-card class="page-card hero-card" shadow="never">
      <template #header>
        <div class="row">
          <div class="row">
            <span class="hero-title">实时资源监测</span>
            <el-tag size="small" type="success" effect="plain" round>
              {{ autoRefresh ? `每 ${refreshInterval}s 刷新` : '已暂停' }}
            </el-tag>
          </div>
          <div class="row" style="gap: 12px">
            <span class="muted" style="margin: 0">
              {{ latest?.hostname || '-' }} · 采样 {{ fmtTime(latest?.timestamp) }}
            </span>
            <el-switch v-model="autoRefresh" size="small" />
            <el-select v-model="refreshInterval" size="small" style="width: 92px" :disabled="!autoRefresh">
              <el-option :value="5" label="5 秒" />
              <el-option :value="10" label="10 秒" />
              <el-option :value="30" label="30 秒" />
            </el-select>
            <el-button size="small" type="primary" :loading="capturing" @click="refreshSnapshot()">
              立即采集
            </el-button>
          </div>
        </div>
      </template>

      <ResourceGauges :snapshot="latest" />

      <el-divider style="margin: 6px 0 14px" />

      <div class="row" style="margin-bottom: 4px">
        <span class="chart-title">性能趋势（最近 {{ historyRows.length }} 个采样点）</span>
        <el-button size="small" text type="primary" @click="loadHistory">刷新曲线</el-button>
      </div>
      <el-empty
        v-if="!historyRows.length"
        description="暂无历史数据，点击「立即采集」开始积累"
        :image-size="70"
      />
      <MetricsLineChart v-else :entries="historyRows" height="300px" />
    </el-card>

    <!-- 统计卡片：自适应网格，卡片数量变化时不会留缺口 -->
    <div class="stat-grid">
      <el-card v-for="card in summaryCards" :key="card.label" class="stat-card" shadow="hover">
        <div class="stat-inner">
          <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
            <el-icon :size="20"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-num">{{ card.value ?? 0 }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 次要信息 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card class="page-card">
          <template #header>
            <div class="row">
              <span>平台健康检查</span>
              <el-tag :type="health.overall === 'ok' ? 'success' : 'warning'" size="small">
                {{ health.overall || '-' }}
              </el-tag>
            </div>
          </template>
          <el-table :data="health.checks ?? []" size="small">
            <el-table-column prop="name" label="检查项" min-width="160" />
            <el-table-column prop="ok" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.ok ? 'success' : (row.critical ? 'danger' : 'info')" size="small">
                  {{ row.ok ? 'OK' : '异常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="critical" label="关键" width="70">
              <template #default="{ row }">{{ row.critical ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
          </el-table>
          <div class="muted">检查时间：{{ fmtTime(health.checked_at) }}</div>
        </el-card>

        <el-card class="page-card" header="运行环境上下文">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item v-for="(v, k) in runtime" :key="k" :label="String(k)">
              {{ typeof v === 'boolean' ? (v ? '是' : '否') : v }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card class="page-card" header="活跃告警">
          <el-empty v-if="!alerts.length" description="暂无告警" :image-size="60" />
          <el-table v-else :data="alerts" size="small">
            <el-table-column prop="rule_id" label="规则" min-width="120" />
            <el-table-column prop="host" label="主机" min-width="110" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="alertTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="信息" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-card>

        <el-card class="page-card" shadow="never">
          <template #header><span>原始快照</span></template>
          <el-collapse>
            <el-collapse-item title="查看快照 JSON" name="raw">
              <pre class="output">{{ pretty(latest) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import api from '@/api'
import ResourceGauges from '@/components/ResourceGauges.vue'
import MetricsLineChart from '@/components/MetricsLineChart.vue'
import { fmtTs } from '@/utils/time'

const summary = ref<Record<string, unknown>>({})
const health = ref<Record<string, any>>({})
const runtime = ref<Record<string, unknown>>({})
const latest = ref<Record<string, unknown> | null>(null)
const alerts = ref<Record<string, any>[]>([])
const historyRows = ref<Record<string, any>[]>([])
const capturing = ref(false)
const autoRefresh = ref(true)
const refreshInterval = ref(10)
let timer: number | undefined

const summaryCards = computed(() => [
  { label: '用户数', value: summary.value.users_total, icon: 'User', color: '#2f6bff', bg: 'rgba(47,107,255,.12)' },
  { label: '资产数', value: summary.value.assets_total, icon: 'Grid', color: '#00b42a', bg: 'rgba(0,180,42,.12)' },
  { label: '活跃告警', value: summary.value.active_alerts, icon: 'WarningFilled', color: '#f56c6c', bg: 'rgba(245,108,108,.12)' },
  { label: '在线会话', value: summary.value.sessions_active, icon: 'Link', color: '#7b3ff2', bg: 'rgba(123,63,242,.12)' },
  { label: 'MCP 工具', value: summary.value.mcp_tools_total, icon: 'Box', color: '#00b4d8', bg: 'rgba(0,180,216,.12)' },
  { label: '审计事件', value: summary.value.audit_events_total, icon: 'Document', color: '#e68619', bg: 'rgba(230,134,25,.12)' },
  { label: '服务数', value: summary.value.services_total, icon: 'Setting', color: '#5b8cff', bg: 'rgba(91,140,255,.12)' },
])

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

function fmtTime(v: unknown) {
  const s = String(v ?? '')
  return s ? fmtTs(s) : '-'
}

function alertTagType(status: string) {
  if (status === 'active') return 'danger'
  if (status === 'escalated') return 'danger'
  if (status === 'acknowledged') return 'warning'
  return 'info'
}

async function refreshAll() {
  const results = await Promise.allSettled([
    api.platform.adminSummary(),
    api.platform.adminHealth(),
    api.platform.runtimeContext(),
  ])
  if (results[0].status === 'fulfilled') summary.value = results[0].value
  if (results[1].status === 'fulfilled') health.value = results[1].value
  if (results[2].status === 'fulfilled') runtime.value = results[2].value
}

async function refreshSnapshot(silent = false) {
  if (!silent) capturing.value = true
  try {
    const res = (await api.monitor.snapshot()) as Record<string, any>
    latest.value = (res?.snapshot ?? res) as Record<string, any>
    alerts.value = ((await api.monitor.alerts()) as unknown[]) as Record<string, any>[]
    await loadHistory().catch(() => undefined)
  } catch {
    /* ignore */
  } finally {
    capturing.value = false
  }
}

async function loadHistory() {
  historyRows.value = await api.monitor.history({ limit: 100 })
}

let polling = false
function startTimer() {
  stopTimer()
  timer = window.setInterval(async () => {
    if (polling) return
    polling = true
    try {
      await refreshSnapshot(true)
    } finally {
      polling = false
    }
  }, refreshInterval.value * 1000)
}

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

watch([autoRefresh, refreshInterval], ([on]) => {
  if (on) startTimer()
  else stopTimer()
})

onMounted(async () => {
  await refreshAll().catch(() => undefined)
  try {
    latest.value = await api.monitor.latest()
    if (!latest.value || !Object.keys(latest.value).length) latest.value = null
    alerts.value = ((await api.monitor.alerts()) as unknown[]) as Record<string, any>[]
    await loadHistory()
  } catch {
    /* ignore */
  }
  if (!latest.value) await refreshSnapshot(true)
  if (autoRefresh.value) startTimer()
})

onBeforeUnmount(stopTimer)
</script>

<style scoped>
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.muted {
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 8px;
}
.hero-card :deep(.el-card__header) {
  padding: 12px 18px;
}
.hero-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}
.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-regular);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.stat-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}
.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
