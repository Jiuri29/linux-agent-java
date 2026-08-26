<template>
  <el-tabs v-model="tab" @tab-change="onTab">
    <el-tab-pane label="监控概览" name="overview">
      <div class="toolbar">
        <el-button type="primary" :loading="capturing" @click="capture">立即采集</el-button>
        <span class="muted">自动刷新</span>
        <el-switch v-model="autoRefresh" />
        <el-select v-model="refreshInterval" style="width: 100px" :disabled="!autoRefresh">
          <el-option :value="5" label="5 秒" />
          <el-option :value="10" label="10 秒" />
          <el-option :value="30" label="30 秒" />
        </el-select>
        <div class="spacer" />
        <span class="muted">主机：{{ latest?.hostname || '-' }} · 采样时间：{{ fmtTime(latest?.timestamp) }}</span>
      </div>

      <ResourceGauges :snapshot="latest" class="page-card" />

      <el-card class="page-card" shadow="never">
        <template #header>
          <div class="row">
            <span>性能趋势（最近 {{ historyRows.length }} 个采样点）</span>
            <el-button size="small" text type="primary" @click="loadHistory">刷新曲线</el-button>
          </div>
        </template>
        <el-empty v-if="!historyRows.length" description="暂无历史数据，点击「立即采集」开始积累" />
        <MetricsLineChart v-else :entries="historyRows" height="340px" />
      </el-card>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-card class="page-card" header="磁盘分区" shadow="never">
            <el-empty v-if="!diskRows.length" description="暂无分区数据" :image-size="60" />
            <el-table v-else :data="diskRows" size="small" border>
              <el-table-column prop="mounted_on" label="挂载点" min-width="110" />
              <el-table-column label="容量" min-width="140">
                <template #default="{ row }">{{ fmtKb(row.used_kb) }} / {{ fmtKb(row.total_kb) }}</template>
              </el-table-column>
              <el-table-column label="使用率" min-width="170">
                <template #default="{ row }">
                  <el-progress
                    :percentage="Number(row.usage_percent ?? 0)"
                    :color="[
                      { color: '#67c23a', percentage: 60 },
                      { color: '#e6a23c', percentage: 85 },
                      { color: '#f56c6c', percentage: 100 },
                    ]"
                  />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="page-card" shadow="never">
            <template #header>
              <div class="row">
                <span>原始快照数据</span>
                <el-radio-group v-model="rawView" size="small">
                  <el-radio-button value="snapshot">评估结果</el-radio-button>
                  <el-radio-button value="os">OS 感知</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <pre class="output">{{ pretty(rawView === 'snapshot' ? latest : osSnap) }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>

    <el-tab-pane name="alerts">
      <template #label>告警<el-badge v-if="activeCount" :value="activeCount" style="margin-left: 4px" /></template>
      <div class="toolbar">
        <el-radio-group v-model="alertFilter" @change="loadAlerts">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="active">活跃</el-radio-button>
          <el-radio-button value="acknowledged">已确认</el-radio-button>
          <el-radio-button value="escalated">已升级</el-radio-button>
          <el-radio-button value="recovered">已恢复</el-radio-button>
        </el-radio-group>
        <el-button @click="loadAlerts">刷新</el-button>
      </div>
      <el-table :data="alerts" size="small" border>
        <el-table-column prop="id" label="ID" min-width="150" show-overflow-tooltip />
        <el-table-column prop="rule_id" label="规则" min-width="130" />
        <el-table-column prop="host" label="主机" min-width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updated_at" label="更新时间" min-width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="warning" :disabled="row.status !== 'active'" @click="transition(row, 'acknowledge')">确认</el-button>
            <el-button size="small" text type="danger" @click="transition(row, 'escalate')">升级</el-button>
            <el-button size="small" text type="success" @click="transition(row, 'recover')">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-row :gutter="16" style="margin-top: 8px">
        <el-col :span="12">
          <el-card class="page-card" header="抑制规则" shadow="never">
            <div class="toolbar">
              <el-input v-model="suppression.pattern" placeholder="匹配模式（如 rule_id 或 host）" style="width: 240px" />
              <el-button type="primary" size="small" @click="addSuppression">添加抑制</el-button>
            </div>
            <pre class="output">{{ pretty(suppressions) }}</pre>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="page-card" header="通知记录" shadow="never">
            <pre class="output">{{ pretty(notifications) }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>

    <el-tab-pane label="告警规则引擎" name="rules">
      <div class="toolbar">
        <el-button type="primary" @click="saveRules">保存规则配置</el-button>
        <el-button @click="loadRules">重载</el-button>
        <el-button type="success" plain @click="simulate">模拟触发测试</el-button>
      </div>
      <el-row :gutter="16">
        <el-col :span="14">
          <el-card class="page-card" header="规则列表" shadow="never">
            <el-table :data="rules" size="small" border>
              <el-table-column prop="id" label="ID" min-width="120" />
              <el-table-column prop="name" label="名称" min-width="140" />
              <el-table-column prop="metric" label="指标" min-width="110" />
              <el-table-column label="阈值条件" min-width="150">
                <template #default="{ row }">{{ row.op }} {{ row.threshold }}</template>
              </el-table-column>
              <el-table-column prop="severity" label="级别" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.severity === 'critical' ? 'danger' : 'warning'" size="small">{{ row.severity }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="enabled" label="启用" width="80">
                <template #default="{ row }">
                  <el-switch :model-value="row.enabled" @change="(v: any) => toggleRule(row, v)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card class="page-card" header="规则 JSON（可编辑）" shadow="never">
            <el-input v-model="rulesText" type="textarea" :rows="18" class="mono" spellcheck="false" />
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>

    <el-tab-pane label="风险扫描" name="risk">
      <div class="toolbar">
        <el-button type="primary" @click="runRiskScan">执行扫描</el-button>
      </div>
      <el-empty v-if="!risk || !Object.keys(risk).length" description="尚未扫描" />
      <pre v-else class="output">{{ pretty(risk) }}</pre>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'
import ResourceGauges from '@/components/ResourceGauges.vue'
import MetricsLineChart from '@/components/MetricsLineChart.vue'

const tab = ref('overview')
const capturing = ref(false)
const latest = ref<Record<string, any> | null>(null)
const osSnap = ref<Record<string, any> | null>(null)
const rawView = ref<'snapshot' | 'os'>('snapshot')
const autoRefresh = ref(true)
const refreshInterval = ref(10)
let timer: number | undefined

const history = reactive({ host: '', metric: '', limit: 100 })
const historyRows = ref<Record<string, any>[]>([])
const diskRows = computed<any[]>(() => (Array.isArray(latest.value?.disks) ? latest.value!.disks : []))

function fmtTime(v: unknown) {
  const s = String(v ?? '')
  return s ? s.replace('T', ' ').slice(0, 19) : '-'
}

function fmtKb(kb: unknown) {
  const v = Number(kb)
  if (!Number.isFinite(v) || v <= 0) return '-'
  const gb = v / 1024 / 1024
  return gb >= 1 ? gb.toFixed(1) + ' GB' : (v / 1024).toFixed(0) + ' MB'
}

const alertFilter = ref('')
const alerts = ref<Record<string, any>[]>([])
const activeCount = computed(() => alerts.value.filter((a) => a.status === 'active').length)
const suppressions = ref<unknown>([])
const notifications = ref<unknown>([])
const suppression = reactive({ pattern: '' })

const rules = ref<Record<string, any>[]>([])
const rulesText = ref('[]')
const risk = ref<Record<string, any> | null>(null)

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

function tagType(status: string) {
  if (status === 'active' || status === 'escalated') return 'danger'
  if (status === 'acknowledged') return 'warning'
  return 'info'
}

async function loadOverview() {
  try {
    latest.value = await api.monitor.latest()
    if (!latest.value || !Object.keys(latest.value).length) latest.value = null
  } catch {
    latest.value = null
  }
  try {
    osSnap.value = await api.monitor.osSnapshot()
  } catch {
    osSnap.value = null
  }
}

async function capture(silent = false) {
  capturing.value = true
  try {
    const res = (await api.monitor.snapshot()) as Record<string, any>
    latest.value = (res?.snapshot ?? res) as Record<string, any>
    if (!silent) ElMessage.success('快照采集完成')
  } catch {
    /* 拦截器已提示 */
  } finally {
    capturing.value = false
  }
}

async function loadHistory() {
  const params: Record<string, unknown> = { limit: history.limit }
  if (history.host) params.host = history.host
  if (history.metric) params.metric = history.metric
  historyRows.value = await api.monitor.history(params)
}

function startTimer() {
  stopTimer()
  timer = window.setInterval(async () => {
    await capture(true)
    await loadHistory().catch(() => undefined)
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
async function loadAlerts() {
  alerts.value = await api.monitor.alerts(alertFilter.value || undefined)
  suppressions.value = await api.monitor.suppressions()
  notifications.value = await api.monitor.notifications()
}

async function transition(row: Record<string, any>, action: 'acknowledge' | 'escalate' | 'recover') {
  const fn = action === 'acknowledge' ? api.monitor.acknowledge : action === 'escalate' ? api.monitor.escalate : api.monitor.recover
  await fn(row.id)
  ElMessage.success(`已${action === 'acknowledge' ? '确认' : action === 'escalate' ? '升级' : '恢复'}`)
  await loadAlerts()
}

async function addSuppression() {
  if (!suppression.pattern) return
  await api.monitor.addSuppression({ pattern: suppression.pattern })
  suppression.pattern = ''
  suppressions.value = await api.monitor.suppressions()
  ElMessage.success('已添加抑制')
}

async function loadRules() {
  rules.value = await api.monitor.alertRules()
  rulesText.value = JSON.stringify(rules.value, null, 2)
}

async function saveRules() {
  try {
    const parsed = JSON.parse(rulesText.value)
    await api.monitor.saveAlertRules({ rules: parsed })
    ElMessage.success('规则已保存')
    await loadRules()
  } catch (e) {
    if (e instanceof SyntaxError) ElMessage.error('JSON 格式错误：' + e.message)
  }
}

async function toggleRule(row: Record<string, any>, enabled: boolean) {
  row.enabled = enabled
  rulesText.value = JSON.stringify(rules.value, null, 2)
  await saveRules()
}

async function simulate() {
  const result = await api.monitor.simulate({})
  ElMessage.success(`模拟完成，命中 ${result.length} 条`)
  await loadAlerts()
}

async function runRiskScan() {
  risk.value = await api.monitor.riskScan()
}

function onTab(name: string | number) {
  if (name === 'alerts') loadAlerts()
  else if (name === 'rules') loadRules()
}

onMounted(async () => {
  await loadOverview()
  await loadHistory()
  if (!latest.value) await capture(true)
  if (autoRefresh.value) startTimer()
})

onBeforeUnmount(stopTimer)
</script>
