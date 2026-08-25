<template>
  <el-row :gutter="16">
    <el-col :span="14">
      <el-card class="page-card" header="外部 Agent API 配置" shadow="never" v-loading="loading">
        <el-form label-width="110px">
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item label="API 地址">
            <el-input v-model="form.base_url" placeholder="https://api.example.com/v1" />
          </el-form-item>
          <el-form-item label="模型/标识">
            <el-input v-model="form.model" placeholder="可选，如 gpt-4o-mini" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="form.api_key" type="password" show-password
              :placeholder="masked ? '已配置（留空保持不变）' : 'sk-...'" />
          </el-form-item>
          <el-form-item label="地址白名单">
            <el-input v-model="form.allowlist_text" type="textarea" :rows="4" class="mono"
              placeholder="每行一个允许的主机或域名" />
          </el-form-item>
          <div class="toolbar">
            <el-button type="primary" @click="save">保存配置</el-button>
            <el-button type="success" plain @click="testConn" :loading="testing">连通性测试</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card class="page-card" header="测试结果" shadow="never">
        <el-empty v-if="!testResult" description="尚未测试" :image-size="60" />
        <pre v-else class="output">{{ pretty(testResult) }}</pre>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card class="page-card" header="说明" shadow="never">
        <ul class="notes">
          <li>ChatOps 通过此 API 调用外部大模型服务。</li>
          <li>API Key 保存后仅以掩码形式回显，不会明文展示。</li>
          <li>启用 SSRF 防护：目标地址必须命中白名单才会放行。</li>
          <li>保存后立即生效，无需重启后端。</li>
        </ul>
      </el-card>
      <el-card class="page-card" header="当前原始配置" shadow="never">
        <pre class="output">{{ pretty(raw) }}</pre>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ops from '@/api/ops'

const loading = ref(false)
const testing = ref(false)
const masked = ref(false)
const testResult = ref<unknown>(null)
const raw = ref<Record<string, unknown>>({})

const form = reactive({
  enabled: false,
  base_url: '',
  model: '',
  api_key: '',
  allowlist_text: '',
})

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

async function load() {
  loading.value = true
  try {
    const res = await ops.agentConfig.get()
    raw.value = res as Record<string, unknown>
    form.enabled = Boolean(res.enabled)
    form.base_url = String(res.base_url ?? '')
    form.model = String(res.model ?? '')
    masked.value = Boolean(res.api_key_set || res.key_masked)
    const allowlist: unknown = res.allowlist ?? res.allowed_hosts ?? []
    form.allowlist_text = Array.isArray(allowlist) ? (allowlist as string[]).join('\n') : ''
  } finally {
    loading.value = false
  }
}

async function save() {
  const body: Record<string, unknown> = {
    enabled: form.enabled,
    base_url: form.base_url,
    model: form.model,
    allowlist: form.allowlist_text.split('\n').map((s) => s.trim()).filter(Boolean),
  }
  if (form.api_key) body.api_key = form.api_key
  await ops.agentConfig.save(body)
  ElMessage.success('配置已保存')
  form.api_key = ''
  await load()
}

async function testConn() {
  testing.value = true
  try {
    testResult.value = await ops.agentConfig.test()
    ElMessage.success('测试完成')
  } catch {
    /* 拦截器已提示 */
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.notes {
  margin: 0;
  padding-left: 18px;
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
}
</style>
