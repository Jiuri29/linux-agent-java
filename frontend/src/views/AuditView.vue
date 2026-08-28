<template>
  <el-tabs v-model="tab" @tab-change="onTab">
    <el-tab-pane label="审计查询" name="query">
      <div class="toolbar">
        <el-input v-model="q.user" placeholder="用户" style="width: 130px" />
        <el-input v-model="q.action" placeholder="动作" style="width: 140px" />
        <el-input v-model="q.event_type" placeholder="事件类型" style="width: 140px" />
        <el-input v-model="q.keyword" placeholder="关键字" style="width: 160px" />
        <el-date-picker v-model="range" type="datetimerange" start-placeholder="开始" end-placeholder="结束" />
        <el-button type="primary" @click="doQuery">查询</el-button>
        <el-button @click="resetQ">重置</el-button>
        <div class="spacer" />
        <el-dropdown>
          <el-button>导出 / 归档</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="exportJsonl">导出 JSONL</el-dropdown-item>
              <el-dropdown-item divided @click="archive">归档（保留 90 天）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-table :data="events" size="small" border max-height="480">
        <el-table-column prop="ts" label="时间" min-width="165">
          <template #default="{ row }">{{ fmtTs(row.ts || row.timestamp) }}</template>
        </el-table-column>
        <el-table-column prop="actor" label="用户" width="110" />
        <el-table-column prop="eventType" label="事件类型" min-width="120" />
        <el-table-column prop="action" label="动作" min-width="130" show-overflow-tooltip />
        <el-table-column prop="result" label="结果" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.result" :type="row.result === 'success' ? 'success' : (row.result === 'failure' || row.result === 'blocked' ? 'danger' : 'warning')"
              size="small">{{ row.result }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="详情" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ fmtDetail(row.detail) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="完整性校验" name="verify">
      <div class="toolbar"><el-button type="primary" @click="verify">校验 Hash 链</el-button></div>
      <el-result v-if="verifyResult" :icon="verifyResult.valid ? 'success' : 'error'"
        :title="verifyResult.valid ? '校验通过：链完整' : '校验失败：链被篡改或损坏'"
        :sub-title="`事件数：${verifyResult.events ?? verifyResult.total ?? '-'} · 断链位置：${verifyResult.broken_at ?? '无'}`" />
    </el-tab-pane>

    <el-tab-pane label="取证视图" name="forensics">
      <div class="toolbar"><el-button @click="loadForensics">刷新</el-button></div>
      <pre class="output">{{ pretty(forensics) }}</pre>
    </el-tab-pane>

    <el-tab-pane label="合规报告" name="compliance">
      <div class="toolbar">
        <el-input-number v-model="complianceDays" :min="1" :max="3650" />
        <span class="muted">天</span>
        <el-button type="primary" @click="loadCompliance">生成报告</el-button>
      </div>
      <pre v-if="compliance" class="output">{{ pretty(compliance) }}</pre>
      <el-empty v-else description="选择时间范围后生成" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ops from '@/api/ops'
import { downloadFile } from '@/api/http'
import { fmtTs as fmtTsShared } from '@/utils/time'

const tab = ref('query')
const events = ref<Record<string, any>[]>([])
const verifyResult = ref<Record<string, any> | null>(null)
const forensics = ref<unknown>(null)
const compliance = ref<unknown>(null)
const complianceDays = ref(30)
const range = ref<[Date, Date] | null>(null)

const q = reactive({
  user: '',
  action: '',
  event_type: '',
  keyword: '',
  risk: '',
})

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

function fmtTs(ts: unknown): string {
  return fmtTsShared(ts)
}

function fmtDetail(d: unknown): string {
  if (d == null || d === '') return '-'
  if (typeof d === 'string') return d
  try {
    const s = JSON.stringify(d)
    return s && s !== '{}' ? s.slice(0, 200) : '-'
  } catch {
    return String(d)
  }
}

function queryParams() {
  const params: Record<string, string> = {}
  for (const [k, v] of Object.entries(q)) {
    if (v) params[k] = v
  }
  if (range.value) {
    params.from = new Date(range.value[0]).toISOString()
    params.to = new Date(range.value[1]).toISOString()
  }
  return params
}

async function doQuery() {
  events.value = await ops.audit.query(queryParams())
}

function resetQ() {
  q.user = ''
  q.action = ''
  q.event_type = ''
  q.keyword = ''
  q.risk = ''
  range.value = null
  doQuery()
}

async function exportJsonl() {
  await downloadFile(ops.audit.exportUrl(queryParams()), 'audit-export.jsonl')
}

async function archive() {
  try {
    await ElMessageBox.confirm('将 90 天前的审计事件归档？', '归档', { type: 'warning' })
  } catch {
    return
  }
  const res = await ops.audit.archive(90)
  ElMessage.success(`归档完成：${JSON.stringify(res).slice(0, 60)}`)
}

async function verify() {
  verifyResult.value = await ops.audit.verify()
}

async function loadForensics() {
  forensics.value = await ops.audit.forensics()
}

async function loadCompliance() {
  compliance.value = await ops.audit.compliance(complianceDays.value)
}

function onTab(name: string | number) {
  if (name === 'forensics') loadForensics()
}

onMounted(doQuery)
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
