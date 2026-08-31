<template>
  <el-row :gutter="16">
    <el-col :span="16">
      <el-card class="page-card" shadow="never" header="对话">
        <div ref="msgBox" class="msgs">
          <el-empty v-if="!messages.length" description="描述你的运维意图，Agent 将评估并执行" :image-size="70" />
          <div v-for="(m, i) in messages" :key="i" :class="['bubble', m.role]">
            <div class="meta">{{ m.role === 'user' ? '我' : 'Agent' }} · {{ m.ts }}</div>
            <div class="text">{{ m.text }}</div>
            <div v-if="m.actions?.length" class="actions">
              <div v-for="a in m.actions" :key="a.id" class="action-item">
                <span class="mono">{{ a.command || a.summary || a.id }}</span>
                <el-tag :type="(a.risk === 'high' ? 'danger' : a.risk === 'medium' ? 'warning' : 'success')" size="small">
                  {{ a.risk || '-' }}
                </el-tag>
                <el-button size="small" type="warning" @click="confirmAction(a)">确认执行</el-button>
              </div>
            </div>
          </div>
        </div>
        <div class="toolbar" style="margin-top: 10px">
          <el-input
            v-model="draft"
            placeholder="描述你的运维意图，如：查看根分区磁盘使用率"
            @keyup.enter="send"
          />
          <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          <el-button @click="cleanup">清理演示数据</el-button>
        </div>
      </el-card>
    </el-col>

    <el-col :span="8">
      <el-card class="page-card" header="意图模板" shadow="never">
        <el-empty v-if="!templates.length" description="暂无模板" :image-size="60" />
        <div v-for="t in templates" :key="t.id ?? t.name" class="tpl" @click="draft = t.text ?? t.template ?? ''">
          {{ t.name }}<div class="muted mono">{{ t.text ?? t.template }}</div>
        </div>
      </el-card>
      <el-card class="page-card" header="会话信息" shadow="never">
        <pre v-if="sessionInfo" class="output">{{ pretty(sessionInfo) }}</pre>
        <el-empty v-else description="暂无会话信息，发送一条意图后会话开始" :image-size="50" />
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ops from '@/api/ops'

interface ChatMsg {
  role: 'user' | 'agent'
  text: string
  ts: string
  actions?: Record<string, any>[]
}

const messages = ref<ChatMsg[]>([])
const draft = ref('')
const sending = ref(false)
const sessionId = ref<string | null>(null)
const templates = ref<Record<string, any>[]>([])
const sessionInfo = ref<Record<string, any> | null>(null)
const msgBox = ref<HTMLElement>()

function now() {
  return new Date().toLocaleTimeString()
}

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

async function scrollBottom() {
  await nextTick()
  msgBox.value?.scrollTo({ top: msgBox.value.scrollHeight })
}

async function loadTemplates() {
  templates.value = await ops.chatops.templates()
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', text, ts: now() })
  draft.value = ''
  scrollBottom()
  sending.value = true
  try {
    const res = (await ops.chatops.message(sessionId.value, text)) as Record<string, any>
    sessionId.value = res.session_id ?? sessionId.value
    const reply = String(res.reply ?? res.message ?? JSON.stringify(res))
    const actions: Record<string, any>[] = res.actions ?? res.pending_actions ?? []
    messages.value.push({
      role: 'agent',
      text: reply,
      ts: now(),
      actions: actions.filter((a) => a.requires_confirm ?? true),
    })
    if (sessionId.value) sessionInfo.value = await ops.chatops.session(sessionId.value)
  } catch (e) {
    const code = e && typeof e === 'object' && 'code' in e ? String((e as any).code) : ''
    if (code === 'invalid_agent_api_config') {
      messages.value.push({
        role: 'agent',
        text: 'Agent API 尚未配置或不可用，请先到「Agent API 配置」页完成设置后再试。',
        ts: now(),
      })
    } else {
      const detail = e && typeof e === 'object' && 'message' in e ? String((e as any).message) : '请求失败'
      messages.value.push({ role: 'agent', text: `处理失败：${detail}`, ts: now() })
    }
  } finally {
    sending.value = false
    scrollBottom()
  }
}

async function confirmAction(a: Record<string, any>) {
  if (!sessionId.value) return
  try {
    const res = (await ops.chatops.confirm(sessionId.value, a.id)) as Record<string, any>
    messages.value.push({
      role: 'agent',
      text: `执行结果：${String(res.status ?? res.result ?? pretty(res))}`,
      ts: now(),
    })
    sessionInfo.value = await ops.chatops.session(sessionId.value)
  } catch {
    /* 拦截器已提示 */
  }
  scrollBottom()
}

async function cleanup() {
  const res = await ops.chatops.demoCleanup()
  ElMessage.success(`清理完成：${pretty(res).slice(0, 80)}`)
}

onMounted(() => {
  loadTemplates().catch(() => {
    /* 拦截器已提示 */
  })
})
</script>

<style scoped>
.msgs {
  height: 420px;
  overflow-y: auto;
  background: var(--el-fill-color-lighter, #fafafa);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  padding: 12px;
}
.bubble {
  max-width: 82%;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
}
.bubble.user {
  margin-left: auto;
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.meta {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.actions {
  margin-top: 6px;
}
.action-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: var(--el-color-warning-light-9, #fdf6ec);
  border: 1px solid var(--el-color-warning-light-8, #faecd8);
  border-radius: 4px;
  margin-bottom: 6px;
}
.tpl {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
}
.tpl:hover {
  border-color: var(--brand);
}
.muted {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
