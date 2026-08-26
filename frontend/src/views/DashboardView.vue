<template>
  <div>
    <el-row :gutter="16">
      <el-col v-for="card in summaryCards" :key="card.label" :span="6">
        <el-card class="page-card stat-card" shadow="hover">
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
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card class="page-card" header="平台健康检查">
          <template #header>
            <div class="row">
              <span>平台健康检查</span>
              <el-tag :type="health.overall === 'ok' ? 'success' : 'warning'">{{ health.overall || '-' }}</el-tag>
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
          <div class="muted">检查时间：{{ health.checked_at || '-' }}</div>
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
        <el-card class="page-card">
          <template #header>
            <div class="row">
              <span>实时资源占用</span>
              <el-button size="small" text type="primary" @click="refreshSnapshot">立即采集</el-button>
            </div>
          </template>
          <ResourceGauges :snapshot="latest" />
          <el-collapse style="margin-top: 8px">
            <el-collapse-item title="原始快照 JSON" name="raw">
              <pre class="output">{{ pretty(latest) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-card>

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
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import api from '@/api'
import ops from '@/api/ops'
import ResourceGauges from '@/components/ResourceGauges.vue'

const summary = ref<Record<string, unknown>>({})
const health = ref<Record<string, any>>({})
const runtime = ref<Record<string, unknown>>({})
const latest = ref<Record<string, unknown> | null>(null)
const alerts = ref<Record<string, any>[]>([])

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

function alertTagType(status: string) {
  if (status === 'active') return 'danger'
  if (status === 'escalated') return 'danger'
  if (status === 'acknowledged') return 'warning'
  return 'info'
}

async function refreshAll() {
  const [s, h, r] = await Promise.all([
    api.platform.adminSummary(),
    api.platform.adminHealth(),
    api.platform.runtimeContext(),
  ])
  summary.value = s
  health.value = h
  runtime.value = r
}

async function refreshSnapshot() {
  try {
    const res = (await api.monitor.snapshot()) as Record<string, any>
    latest.value = (res?.snapshot ?? res) as Record<string, any>
    alerts.value = ((await api.monitor.alerts()) as unknown[]) as Record<string, any>[]
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  await refreshAll()
  try {
    latest.value = await api.monitor.latest()
    if (!latest.value || !Object.keys(latest.value).length) latest.value = null
    alerts.value = ((await api.monitor.alerts()) as unknown[]) as Record<string, any>[]
  } catch {
    /* ignore */
  }
})
</script>

<style scoped>
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.muted {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
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
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
</style>
