<template>
  <el-tabs v-model="tab" @tab-change="onTab">
    <el-tab-pane label="拦截规则" name="rules">
      <div class="toolbar">
        <el-button type="primary" @click="openRule()">新建规则</el-button>
        <el-button @click="loadRules">刷新</el-button>
      </div>
      <el-table :data="rules" size="small" border>
        <el-table-column prop="id" label="ID" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="pattern" label="匹配模式" min-width="220">
          <template #default="{ row }"><span class="mono">{{ row.pattern }}</span></template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="100">
          <template #default="{ row }">
            <el-tag :type="row.action === 'block' ? 'danger' : row.action === 'confirm' ? 'warning' : 'info'" size="small">
              {{ row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v: any) => toggleRule(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="builtin" label="内置" width="70">
          <template #default="{ row }">{{ row.builtin ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="豁免管理" name="exemptions">
      <div class="toolbar">
        <el-input v-model="newExemption.pattern" placeholder="豁免模式（命令前缀/正则）" style="width: 280px" />
        <el-input v-model="newExemption.reason" placeholder="原因（可选）" style="width: 200px" />
        <el-button type="primary" @click="addExemption">添加豁免</el-button>
        <el-button @click="loadExemptions">刷新</el-button>
      </div>
      <pre class="output">{{ pretty(exemptions) }}</pre>
    </el-tab-pane>

    <el-tab-pane label="审批记录" name="approvals">
      <div class="toolbar"><el-button @click="loadApprovals">刷新</el-button></div>
      <el-table :data="approvals" size="small" border max-height="480">
        <el-table-column prop="ts" label="时间" min-width="160">
          <template #default="{ row }">{{ row.ts || row.created_at || '-' }}</template>
        </el-table-column>
        <el-table-column prop="intent" label="意图" min-width="180" show-overflow-tooltip />
        <el-table-column prop="command" label="命令" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.command }}</span></template>
        </el-table-column>
        <el-table-column prop="risk" label="风险" width="90">
          <template #default="{ row }">
            <el-tag :type="row.risk === 'high' ? 'danger' : row.risk === 'medium' ? 'warning' : 'success'" size="small">
              {{ row.risk }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" />
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="风险评估测试" name="test">
      <el-row :gutter="16">
        <el-col :span="10">
          <el-card class="page-card" header="输入" shadow="never">
            <el-form label-position="top">
              <el-form-item label="意图描述">
                <el-input v-model="testForm.intent" type="textarea" :rows="3" />
              </el-form-item>
              <el-form-item label="命令（每行一条）">
                <el-input v-model="testForm.commandsText" type="textarea" :rows="5" class="mono" spellcheck="false" />
              </el-form-item>
              <el-button type="primary" @click="runEvaluate">评估</el-button>
            </el-form>
          </el-card>
        </el-col>
        <el-col :span="14">
          <el-card class="page-card" header="评估结果" shadow="never">
            <el-empty v-if="!evaluateResult" description="尚未评估" />
            <pre v-else class="output">{{ pretty(evaluateResult) }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="ruleDlg.visible" title="新建/编辑规则" width="520px">
    <el-form label-width="80px">
      <el-form-item label="名称"><el-input v-model="ruleDlg.name" /></el-form-item>
      <el-form-item label="模式">
        <el-input v-model="ruleDlg.pattern" class="mono" placeholder="正则或关键字，如 rm\s+-rf" />
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="ruleDlg.action" style="width: 160px">
          <el-option value="block" /><el-option value="confirm" /><el-option value="audit" />
        </el-select>
      </el-form-item>
      <el-form-item label="说明"><el-input v-model="ruleDlg.description" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="ruleDlg.visible = false">取消</el-button>
      <el-button type="primary" @click="saveRule">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ops from '@/api/ops'

const tab = ref('rules')
const rules = ref<Record<string, any>[]>([])
const exemptions = ref<unknown>([])
const approvals = ref<Record<string, any>[]>([])
const evaluateResult = ref<unknown>(null)

const newExemption = reactive({ pattern: '', reason: '' })
const ruleDlg = reactive({ visible: false, id: '', name: '', pattern: '', action: 'block', description: '' })
const testForm = reactive({ intent: '', commandsText: '' })

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

async function loadRules() {
  rules.value = await ops.guardrails.rules()
}

async function loadExemptions() {
  exemptions.value = await ops.guardrails.exemptions()
}

async function loadApprovals() {
  approvals.value = await ops.guardrails.approvals(100)
}

function openRule(row?: Record<string, any>) {
  ruleDlg.id = row?.id ?? ''
  ruleDlg.name = row?.name ?? ''
  ruleDlg.pattern = row?.pattern ?? ''
  ruleDlg.action = row?.action ?? 'block'
  ruleDlg.description = row?.description ?? ''
  ruleDlg.visible = true
}

async function saveRule() {
  const body: Record<string, unknown> = { name: ruleDlg.name, pattern: ruleDlg.pattern, action: ruleDlg.action }
  if (ruleDlg.id) body.id = ruleDlg.id
  if (ruleDlg.description) body.description = ruleDlg.description
  await ops.guardrails.saveRule(body)
  ElMessage.success('规则已保存')
  ruleDlg.visible = false
  await loadRules()
}

async function toggleRule(row: Record<string, any>, enabled: boolean) {
  await ops.guardrails.setRuleStatus({ id: row.id, enabled })
  ElMessage.success(enabled ? '已启用' : '已停用')
  await loadRules()
}

async function addExemption() {
  if (!newExemption.pattern) return
  await ops.guardrails.addExemption({ ...newExemption })
  newExemption.pattern = ''
  newExemption.reason = ''
  ElMessage.success('豁免已添加')
  await loadExemptions()
}

async function runEvaluate() {
  const commands = testForm.commandsText.split('\n').map((s) => s.trim()).filter(Boolean)
  evaluateResult.value = await ops.guardrails.evaluate(testForm.intent, commands)
}

function onTab(name: string | number) {
  if (name === 'exemptions') loadExemptions()
  else if (name === 'approvals') loadApprovals()
}

onMounted(loadRules)
</script>
