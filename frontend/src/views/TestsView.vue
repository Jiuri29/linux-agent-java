<template>
  <el-row :gutter="16">
    <el-col :span="10">
      <el-card class="page-card" header="自检用例目录" shadow="never">
        <div class="toolbar">
          <el-button type="primary" :loading="running" @click="runAll">执行全部</el-button>
          <el-button size="small" :disabled="!selected.length" @click="runSelected">执行所选（{{ selected.length }}）</el-button>
          <el-button @click="loadCatalog">刷新</el-button>
        </div>
        <el-table ref="tableRef" :data="cases" size="small" border max-height="420"
          @selection-change="(sels: any[]) => (selected = sels)">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="id" label="用例 ID" min-width="170" show-overflow-tooltip />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="module" label="模块" width="110" />
        </el-table>
      </el-card>
    </el-col>

    <el-col :span="14">
      <el-card class="page-card" header="运行记录" shadow="never">
        <div class="toolbar"><el-button size="small" @click="loadRuns">刷新</el-button></div>
        <el-table :data="runs" size="small" border max-height="240" highlight-current-row @row-click="showRun">
          <el-table-column prop="id" label="运行 ID" min-width="160" show-overflow-tooltip />
          <el-table-column prop="ts" label="时间" min-width="150">
            <template #default="{ row }">{{ row.ts || row.created_at || '-' }}</template>
          </el-table-column>
          <el-table-column prop="trigger" label="触发方式" width="100" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="报告" width="80" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click.stop="openReport(row)">MD</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="page-card" header="运行详情" shadow="never">
        <el-empty v-if="!detail" description="点击上方记录查看" :image-size="60" />
        <pre v-else class="output">{{ pretty(detail) }}</pre>
      </el-card>

      <el-dialog v-model="reportDlg.visible" title="Markdown 报告" width="760px">
        <pre class="output">{{ reportDlg.text }}</pre>
      </el-dialog>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ops from '@/api/ops'
import http from '@/api/http'

const cases = ref<Record<string, any>[]>([])
const runs = ref<Record<string, any>[]>([])
const selected = ref<Record<string, any>[]>([])
const running = ref(false)
const detail = ref<unknown>(null)
const reportDlg = reactive({ visible: false, text: '' })

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

async function loadCatalog() {
  const res = await ops.tests.catalog()
  cases.value = (Array.isArray(res) ? res : res.cases ?? []) as Record<string, any>[]
}

async function loadRuns() {
  runs.value = await ops.tests.runs(20)
}

async function runAll() {
  running.value = true
  try {
    await ops.tests.run([])
    ElMessage.success('执行完成')
    await loadRuns()
  } catch {
    /* 拦截器已提示 */
  } finally {
    running.value = false
  }
}

async function runSelected() {
  running.value = true
  try {
    await ops.tests.run(selected.value.map((c) => String(c.id)))
    ElMessage.success('执行完成')
    await loadRuns()
  } catch {
    /* 拦截器已提示 */
  } finally {
    running.value = false
  }
}

async function showRun(row: Record<string, any>) {
  detail.value = await ops.tests.runDetail(row.id)
}

async function openReport(row: Record<string, any>) {
  const res = await http.get<string>(ops.tests.reportUrl(row.id), { responseType: 'text' })
  reportDlg.text = String(res)
  reportDlg.visible = true
}

onMounted(() => {
  loadCatalog()
  loadRuns()
})
</script>
