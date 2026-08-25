<template>
  <el-tabs v-model="tab" @tab-change="onTab">
    <el-tab-pane label="监控概览" name="overview">
      <div class="toolbar">
        <el-button type="primary" :loading="capturing" @click="capture">采集快照</el-button>
        <el-button @click="loadOverview">刷新</el-button>
      </div>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card class="page-card" header="最新评估结果" shadow="never">
            <el-empty v-if="!latest || !Object.keys(latest).length" description="暂无数据" :image-size="60" />
            <pre v-else class="output">{{ pretty(latest) }}</pre>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="page-card" header="OS 只读感知" shadow="never">
            <el-empty v-if="!osSnap || !Object.keys(osSnap).length" description="暂无数据" :image-size="60" />
            <pre v-else class="output">{{ pretty(osSnap) }}</pre>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="page-card" header="指标历史" shadow="never">
        <div class="toolbar">
          <el-input v-model="history.host" placeholder="主机（可选）" style="width: 180px" />
          <el-input v-model="history.metric" placeholder="指标（可选）" style="width: 160px" />
          <el-input-number v-model="history.limit" :min="10" :max="1000" :step="50" />
          <el-button type="primary" @click="loadHistory">查询</el-button>
        </div>
        <el-table :data="historyRows" size="small" border max-height="360">
          <el-table-column prop="ts" label="时间" min-width="170">
            <template #default="{ row }">{{ row.ts || row.timestamp || row.created_at || '-' }}</template>
          </el-table-column>
          <el-table-column prop="host" label="主机" min-width="130" />
          <el-table-column prop="metric" label="指标" min-width="120" />
          <el-table-column prop="value" label="值" width="110" />
        </el-table>
      </el-card>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

const tab = ref('overview')
const capturing = ref(false)
const latest = ref<Record<string, any> | null>(null)
const osSnap = ref<Record<string, any> | null>(null)

const history = reactive({ host: '', metric: '', limit: 100 })
const historyRows = ref<Record<string, any>[]>([])

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

async function capture() {
  capturing.value = true
  try {
    latest.value = await api.monitor.snapshot()
    ElMessage.success('快照采集完成')
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

onMounted(() => {
  loadOverview()
  loadHistory()
})
</script>
