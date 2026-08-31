<template>
  <el-tabs v-model="tab" @tab-change="onTab">
    <el-tab-pane label="内置工具集" name="tools">
      <div class="toolbar">
        <el-button @click="loadTools">刷新</el-button>
        <el-button @click="showCapabilities">查看能力描述</el-button>
        <div class="spacer" />
        <span class="muted">{{ tools.length }} 个工具</span>
      </div>
      <el-table v-loading="toolsLoading" :data="tools" size="small" border>
        <el-table-column prop="name" label="工具名" min-width="180" />
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled !== false" @change="(v: any) => toggleTool(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openInvoke(row)">调用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="插件管理" name="plugins">
      <div class="toolbar">
        <el-button type="primary" @click="regDlg.visible = true">注册插件</el-button>
        <el-button @click="loadPlugins">刷新</el-button>
      </div>
      <el-table v-loading="pluginsLoading" :data="plugins" size="small" border>
        <el-table-column prop="name" label="插件名" min-width="150" />
        <el-table-column label="提供工具" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="t in row.provided_tools ?? []" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="warning" @click="togglePlugin(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" text type="danger" @click="uninstall(row)">卸载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="invokeDlg.visible" :title="`调用工具：${invokeDlg.name}`" width="640px">
    <el-input v-model="invokeDlg.paramsText" type="textarea" :rows="8" class="mono" spellcheck="false"
      placeholder='{"path": "/"}' />
    <pre v-if="invokeDlg.result" class="output" style="margin-top: 10px">{{ invokeDlg.result }}</pre>
    <template #footer>
      <el-button @click="invokeDlg.visible = false">关闭</el-button>
      <el-button type="primary" :loading="invokeDlg.loading" @click="doInvoke">执行</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="capDlg.visible" title="MCP 能力描述 (capabilities)" width="640px">
    <pre class="output">{{ pretty(capabilities) }}</pre>
  </el-dialog>

  <el-dialog v-model="regDlg.visible" title="注册插件" width="560px">
    <el-form label-width="90px">
      <el-form-item label="名称"><el-input v-model="regForm.name" /></el-form-item>
      <el-form-item label="来源类型">
        <el-select v-model="regForm.source_type" style="width: 200px">
          <el-option value="inline" /><el-option value="url" /><el-option value="file" />
        </el-select>
      </el-form-item>
      <el-form-item label="清单 JSON">
        <el-input v-model="regForm.manifestText" type="textarea" :rows="8" class="mono" spellcheck="false"
          placeholder='{"tools":[{"name":"echo","description":"...","params":{}}]}' />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="regDlg.visible = false">取消</el-button>
      <el-button type="primary" @click="register">注册</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ops from '@/api/ops'

const tab = ref('tools')
const tools = ref<Record<string, any>[]>([])
const toolsLoading = ref(false)
const plugins = ref<Record<string, any>[]>([])
const pluginsLoading = ref(false)
const capabilities = ref<unknown>(null)

const invokeDlg = reactive({ visible: false, name: '', paramsText: '{}', result: '', loading: false })
const capDlg = reactive({ visible: false })
const regDlg = reactive({ visible: false })
const regForm = reactive({ name: '', source_type: 'inline', manifestText: '{}' })

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

async function loadTools() {
  toolsLoading.value = true
  try {
    tools.value = await ops.mcp.tools()
  } finally {
    toolsLoading.value = false
  }
}

async function loadPlugins() {
  pluginsLoading.value = true
  try {
    plugins.value = await ops.mcp.plugins()
  } finally {
    pluginsLoading.value = false
  }
}

function openInvoke(row: Record<string, any>) {
  invokeDlg.name = row.name
  invokeDlg.paramsText = JSON.stringify(row.example_params ?? {}, null, 2)
  invokeDlg.result = ''
  invokeDlg.visible = true
}

async function doInvoke() {
  invokeDlg.loading = true
  try {
    const params = JSON.parse(invokeDlg.paramsText || '{}')
    const res = await ops.mcp.invoke(invokeDlg.name, params)
    invokeDlg.result = pretty(res)
  } catch (e) {
    if (e instanceof SyntaxError) ElMessage.error('JSON 参数格式错误')
  } finally {
    invokeDlg.loading = false
  }
}

async function showCapabilities() {
  capabilities.value = await ops.mcp.capabilities()
  capDlg.visible = true
}

async function toggleTool(row: Record<string, any>, enabled: boolean) {
  await ops.mcp.setToolStatus({ name: row.name, enabled })
  ElMessage.success(enabled ? '已启用' : '已停用')
  await loadTools()
}

async function togglePlugin(row: Record<string, any>) {
  await ops.mcp.setPluginStatus({ id: row.id, enabled: !row.enabled })
  await loadPlugins()
}

async function uninstall(row: Record<string, any>) {
  await ElMessageBox.confirm(`确认卸载插件「${row.name}」？`, '卸载', { type: 'warning' })
  await ops.mcp.uninstallPlugin({ id: row.id })
  ElMessage.success('已卸载')
  await loadPlugins()
}

async function register() {
  try {
    const manifest = JSON.parse(regForm.manifestText || '{}')
    await ops.mcp.registerPlugin({ name: regForm.name, source_type: regForm.source_type, manifest })
    ElMessage.success('插件已注册')
    regDlg.visible = false
    tab.value = 'plugins'
    await loadPlugins()
  } catch (e) {
    if (e instanceof SyntaxError) ElMessage.error('清单 JSON 格式错误')
  }
}

function onTab(name: string | number) {
  if (name === 'plugins') loadPlugins()
}

onMounted(loadTools)
</script>

<style scoped>
.muted {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
