<template>
  <el-row :gutter="16">
    <el-col :span="14">
      <el-card class="page-card" header="命令执行（allowlist 只读策略）" shadow="never">
        <div class="toolbar">
          <el-input
            v-model="cmd"
            class="mono"
            placeholder="输入允许的只读命令，如 uptime / df -h / free -m"
            @keyup.enter="run"
          />
          <el-button type="primary" :loading="running" @click="run">执行</el-button>
        </div>
        <div v-if="templates.length" class="toolbar">
          <el-select v-model="selectedTemplate" placeholder="从模板选择" style="width: 240px" @change="renderTpl">
            <el-option v-for="t in templates" :key="t.id" :value="t.id" :label="t.name" />
          </el-select>
          <el-button size="small" @click="openSaveTemplate">存为模板</el-button>
        </div>
        <pre v-if="output" class="output">{{ output }}</pre>
      </el-card>

      <el-card class="page-card" header="执行历史" shadow="never">
        <div class="toolbar"><el-button size="small" @click="loadHistory">刷新</el-button></div>
        <el-table :data="historyRows" size="small" border max-height="320">
          <el-table-column prop="ts" label="时间" min-width="160">
            <template #default="{ row }">{{ fmtTs(row.ts || row.created_at) }}</template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" min-width="100" />
          <el-table-column prop="command" label="命令" min-width="220" show-overflow-tooltip>
            <template #default="{ row }"><span class="mono">{{ row.command }}</span></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ok' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="replay(row)">回放</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card class="page-card" header="命令模板" shadow="never">
        <el-empty v-if="!templates.length" description="暂无模板" :image-size="60" />
        <el-table v-else :data="templates" size="small" border>
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="command_template" label="模板" min-width="200" show-overflow-tooltip>
            <template #default="{ row }"><span class="mono">{{ row.command_template }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="applyTemplate(row)">填参数</el-button>
              <el-button size="small" text type="warning" @click="saveAsCopy(row)">复制</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-dialog v-model="tplDlg.visible" title="保存命令模板" width="520px">
        <el-form label-width="80px">
          <el-form-item label="名称"><el-input v-model="tplDlg.name" /></el-form-item>
          <el-form-item label="模板">
            <el-input v-model="tplDlg.template" class="mono" placeholder="如 df -h {path}，{param} 为占位符" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="tplDlg.visible = false">取消</el-button>
          <el-button type="primary" @click="saveTemplate">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="paramDlg.visible" title="渲染模板参数" width="480px">
        <el-input v-model="paramDlg.paramsText" type="textarea" :rows="6" class="mono" spellcheck="false"
          placeholder='{"path": "/var"}' />
        <template #footer>
          <el-button @click="paramDlg.visible = false">取消</el-button>
          <el-button type="primary" @click="doRender">渲染并填充</el-button>
        </template>
      </el-dialog>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

const cmd = ref('')
const running = ref(false)
const output = ref('')
const templates = ref<Record<string, any>[]>([])
const historyRows = ref<Record<string, any>[]>([])
const selectedTemplate = ref('')

const tplDlg = reactive({ visible: false, name: '', template: '' })
const paramDlg = reactive({ visible: false, paramsText: '{}', templateId: '' })

function fmtTs(ts: unknown): string {
  const s = String(ts ?? '')
  const d = new Date(s)
  if (!Number.isFinite(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

async function loadTemplates() {
  templates.value = await api.terminal.templates()
}

async function loadHistory() {
  historyRows.value = await api.terminal.history(50)
}

async function run() {
  if (!cmd.value.trim()) return
  running.value = true
  try {
    const res = await api.terminal.run({ command: cmd.value })
    output.value =
      typeof res === 'string'
        ? res
        : String(res.stdout || res.stderr || res.output || pretty(res) || '')
    await loadHistory()
  } catch {
    /* 拦截器已提示 */
  } finally {
    running.value = false
  }
}

function openSaveTemplate() {
  if (!cmd.value.trim()) {
    ElMessage.warning('请先输入命令')
    return
  }
  tplDlg.name = ''
  tplDlg.template = cmd.value
  tplDlg.visible = true
}

function saveAsCopy(row: Record<string, any>) {
  tplDlg.name = row.name + '-copy'
  tplDlg.template = row.command_template
  tplDlg.visible = true
}

async function saveTemplate() {
  await api.terminal.saveTemplate({ name: tplDlg.name, command_template: tplDlg.template })
  ElMessage.success('模板已保存')
  tplDlg.visible = false
  await loadTemplates()
}

function applyTemplate(row: Record<string, any>) {
  paramDlg.templateId = row.id
  paramDlg.paramsText = '{}'
  paramDlg.visible = true
}

function renderTpl(id: string) {
  const row = templates.value.find((t) => t.id === id)
  if (row) applyTemplate(row)
}

async function doRender() {
  try {
    const params = JSON.parse(paramDlg.paramsText || '{}')
    const res = await api.terminal.renderTemplate(paramDlg.templateId, params)
    cmd.value = String(res.command ?? pretty(res))
    paramDlg.visible = false
  } catch (e) {
    if (e instanceof SyntaxError) ElMessage.error('JSON 参数格式错误')
  }
}

async function replay(row: Record<string, any>) {
  const res = await api.terminal.replay(row.id)
  output.value = typeof res === 'string' ? res : String(res.output ?? pretty(res))
}

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

onMounted(() => {
  loadTemplates()
  loadHistory()
})
</script>
