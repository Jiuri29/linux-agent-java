<template>
  <div class="toolbar">
    <el-button type="primary" @click="openEdit()">新建/更新服务</el-button>
    <el-button @click="load">刷新</el-button>
    <div class="spacer" />
    <span class="muted">点击行打开服务详情</span>
  </div>

  <el-table v-loading="loading" :data="rows" size="small" border highlight-current-row @row-click="openDetail">
    <el-table-column prop="id" label="ID" min-width="140" show-overflow-tooltip />
    <el-table-column prop="name" label="名称" min-width="150" />
    <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
    <el-table-column prop="state" label="状态" width="110">
      <template #default="{ row }">
        <el-tag v-if="row.state || row.status" size="small">{{ row.state || row.status }}</el-tag>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column prop="version" label="当前版本" width="110" />
  </el-table>

  <el-drawer v-model="detail.visible" :title="`服务：${detail.id}`" size="720px">
    <el-tabs v-model="detail.tab">
      <el-tab-pane label="配置版本" name="versions">
        <div class="toolbar">
          <el-button type="primary" size="small" @click="newVersionDraft">保存为新版本</el-button>
          <el-button size="small" @click="loadVersions">刷新</el-button>
          <div class="spacer" />
          <span class="muted">选择两个版本做 diff</span>
        </div>
        <el-table
          :data="versions"
          size="small"
          border
          max-height="260"
          @selection-change="(sels: any[]) => (selected = sels)"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="id" label="版本 ID" min-width="170" show-overflow-tooltip />
          <el-table-column prop="created_at" label="创建时间" min-width="160" />
          <el-table-column prop="author" label="作者" width="100" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="viewVersion(row)">内容</el-button>
              <el-button size="small" text type="warning" @click="rollback(row)">回滚到此版</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="toolbar" style="margin-top: 10px">
          <el-button size="small" :disabled="selected.length !== 2" @click="diffSelected">对比所选版本</el-button>
          <el-button size="small" @click="checkDrift">检测漂移</el-button>
        </div>
        <pre v-if="diffText" class="output">{{ diffText }}</pre>
      </el-tab-pane>

      <el-tab-pane label="当前配置读取" name="read">
        <div class="toolbar"><el-button size="small" @click="readConfig">读取线上配置</el-button></div>
        <pre class="output">{{ pretty(readResult) }}</pre>
      </el-tab-pane>

      <el-tab-pane label="生命周期与变更" name="lifecycle">
        <div class="toolbar">
          <el-select v-model="action" style="width: 160px">
            <el-option value="deploy" label="deploy" />
            <el-option value="start" label="start" />
            <el-option value="stop" label="stop" />
            <el-option value="restart" label="restart" />
          </el-select>
          <el-button type="primary" @click="runLifecycle">执行</el-button>
        </div>
        <el-card header="变更记录" shadow="never">
          <el-table :data="changes" size="small" border max-height="300">
            <el-table-column prop="ts" label="时间" min-width="160">
              <template #default="{ row }">{{ row.ts || row.created_at || '-' }}</template>
            </el-table-column>
            <el-table-column prop="action" label="动作" min-width="100" />
            <el-table-column prop="operator" label="操作人" min-width="100" />
            <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>

  <el-dialog v-model="edit.visible" title="新建/更新服务" width="560px">
    <el-form label-width="90px">
      <el-form-item label="ID"><el-input v-model="form.id" placeholder="唯一标识" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
      <el-form-item label="初始配置">
        <el-input v-model="form.content" type="textarea" :rows="6" spellcheck="false" placeholder="配置文件内容（可选）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="edit.visible = false">取消</el-button>
      <el-button type="primary" @click="saveService">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="verDlg.visible" title="保存新版本" width="640px">
    <el-input v-model="verDlg.content" type="textarea" :rows="14" spellcheck="false" />
    <template #footer>
      <el-button @click="verDlg.visible = false">取消</el-button>
      <el-button type="primary" @click="saveVersion">保存版本</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="viewDlg.visible" title="版本内容" width="640px">
    <pre class="output">{{ pretty(viewDlg.data) }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const loading = ref(false)
const rows = ref<Record<string, any>[]>([])
const selected = ref<Record<string, any>[]>([])
const versions = ref<Record<string, any>[]>([])
const changes = ref<Record<string, any>[]>([])
const diffText = ref('')
const readResult = ref<unknown>(null)
const action = ref('deploy')

const detail = reactive({ visible: false, id: '', tab: 'versions' })
const edit = reactive({ visible: false })
const form = reactive({ id: '', name: '', description: '', content: '' })
const verDlg = reactive({ visible: false, content: '' })
const viewDlg = reactive({ visible: false, data: null as unknown })

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

async function load() {
  loading.value = true
  try {
    const res = await api.services.list()
    rows.value = (Array.isArray(res) ? res : res.services ?? []) as Record<string, any>[]
  } finally {
    loading.value = false
  }
}

function openEdit() {
  edit.visible = true
}

async function saveService() {
  if (!form.id) {
    ElMessage.warning('请填写服务 ID')
    return
  }
  const body: Record<string, unknown> = { id: form.id, name: form.name, description: form.description }
  if (form.content) body.content = form.content
  await api.services.upsert(body)
  ElMessage.success('已保存')
  edit.visible = false
  await load()
}

async function openDetail(row: Record<string, any>) {
  detail.id = row.id
  detail.visible = true
  detail.tab = 'versions'
  diffText.value = ''
  readResult.value = null
  changes.value = []
  versions.value = []
  selected.value = []
  await Promise.all([loadVersions(), loadChanges()])
}

async function loadVersions() {
  if (!detail.id) return
  const res = await api.services.versions(detail.id)
  versions.value = (Array.isArray(res) ? res : res.versions ?? []) as Record<string, any>[]
}

async function loadChanges() {
  if (!detail.id) return
  const res = await api.services.changes({ service_id: detail.id, limit: 100 })
  changes.value = (Array.isArray(res) ? res : res.changes ?? []) as Record<string, any>[]
}
function newVersionDraft() {
  verDlg.content = ''
  verDlg.visible = true
}

async function saveVersion() {
  await api.services.saveVersion({ service_id: detail.id, content: verDlg.content })
  ElMessage.success('版本已保存')
  verDlg.visible = false
  await loadVersions()
  await loadChanges()
}

async function viewVersion(row: Record<string, any>) {
  viewDlg.data = await api.services.versionDetail(row.id)
  viewDlg.visible = true
}

async function diffSelected() {
  const [a, b] = selected.value
  const res = await api.services.diff(a.id, b.id)
  diffText.value = String(res.unified ?? res.diff ?? pretty(res))
}

async function checkDrift() {
  const res = await api.services.drift(detail.id, selected.value[0]?.id)
  diffText.value = pretty(res)
}

async function rollback(row: Record<string, any>) {
  await ElMessageBox.confirm(`确认回滚到版本 ${row.id}？`, '回滚', { type: 'warning' })
  await api.services.rollback({ service_id: detail.id, version_id: row.id })
  ElMessage.success('已回滚')
  await Promise.all([loadVersions(), loadChanges()])
}

async function readConfig() {
  readResult.value = await api.services.readConfig(detail.id)
}

async function runLifecycle() {
  await api.services.lifecycle({ id: detail.id, action: action.value })
  ElMessage.success(`已执行 ${action.value}`)
  await loadChanges()
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
