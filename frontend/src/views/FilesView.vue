<template>
  <el-row :gutter="16">
    <el-col :span="14">
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <el-input v-model="path" class="mono" placeholder="/etc 或 C:\Windows" @keyup.enter="browse">
            <template #prepend>路径</template>
          </el-input>
          <el-button type="primary" @click="browse">打开</el-button>
          <el-button @click="goUp">上级</el-button>
          <el-button @click="browse">刷新</el-button>
        </div>

        <el-table v-loading="loading" :data="entries" size="small" border max-height="440"
          @row-dblclick="(row: any) => row.is_dir && enter(row)">
          <el-table-column prop="name" label="名称" min-width="220">
            <template #default="{ row }">
              <el-icon style="vertical-align: -2px"><component :is="row.is_dir ? 'Folder' : 'Document'" /></el-icon>
              <span class="mono" style="margin-left: 6px">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ row.is_dir ? '目录' : '文件' }}</template>
          </el-table-column>
          <el-table-column prop="size" label="大小" width="100">
            <template #default="{ row }">{{ row.is_dir ? '-' : formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column prop="mtime" label="修改时间" min-width="150" />
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.is_dir" size="small" text type="primary" @click="enter(row)">进入</el-button>
              <template v-else>
                <el-button size="small" text type="primary" @click="viewFile(row)">查看</el-button>
                <el-button size="small" text type="primary" @click="download(row)">下载</el-button>
              </template>
              <el-button size="small" text type="warning" @click="showBackups(row)">备份</el-button>
              <el-button size="small" text type="danger" @click="removeEntry(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card class="page-card" header="上传 / 新建文件" shadow="never">
        <el-form label-width="70px" size="small">
          <el-form-item label="目标路径">
            <el-input v-model="targetPath" class="mono" />
          </el-form-item>
          <el-form-item label="上传文件">
            <input type="file" @change="onPickFile" />
          </el-form-item>
          <el-button size="small" type="primary" :disabled="!picked" :loading="uploading" @click="upload">上传</el-button>
          <el-button size="small" @click="createEmpty">新建空文件</el-button>
        </el-form>
      </el-card>

      <el-card class="page-card" header="备份与恢复" shadow="never">
        <div class="muted" style="margin-bottom: 8px">当前文件：{{ backupPath || '（先在左侧点「备份」）' }}</div>
        <el-table v-loading="backupsLoading" :data="backups" size="small" border max-height="240">
          <el-table-column prop="id" label="备份 ID" min-width="170" show-overflow-tooltip />
          <el-table-column prop="ts" label="时间" min-width="140">
            <template #default="{ row }">{{ row.ts || row.created_at || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="diffBackup(row)">diff</el-button>
              <el-button size="small" text type="warning" @click="restoreBackup(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
  </el-row>

  <el-dialog v-model="editor.visible" :title="`编辑：${editor.path}`" width="720px">
    <el-input v-model="editor.content" type="textarea" :rows="18" class="mono" spellcheck="false" />
    <template #footer>
      <el-button @click="editor.visible = false">关闭</el-button>
      <el-button type="primary" @click="saveEditor">保存写入</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="diffDlg.visible" title="备份 diff" width="720px">
    <pre class="output">{{ diffDlg.text }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import { downloadFile } from '@/api/http'

const loading = ref(false)
const path = ref('')
const entries = ref<Record<string, any>[]>([])
const targetPath = ref('')
const roots = ref<string[]>([])
const picked = ref<File | null>(null)
const uploading = ref(false)

const editor = reactive({ visible: false, path: '', content: '' })
const backups = ref<Record<string, any>[]>([])
const backupsLoading = ref(false)
const backupPath = ref('')
const diffDlg = reactive({ visible: false, text: '' })

function formatSize(n: unknown) {
  const v = Number(n)
  if (Number.isNaN(v)) return '-'
  if (v < 1024) return `${v} B`
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(1)} KB`
  if (v < 1024 * 1024 * 1024) return `${(v / 1024 / 1024).toFixed(1)} MB`
  return `${(v / 1024 / 1024 / 1024).toFixed(1)} GB`
}

let browseSeq = 0
async function browse() {
  const seq = ++browseSeq
  loading.value = true
  try {
    const res = await api.files.list(path.value || undefined)
    if (seq !== browseSeq) return
    const list = Array.isArray(res) ? res : res.entries ?? res.items ?? []
    entries.value = list as Record<string, any>[]
    const base = path.value || roots.value[0] || '/'
    targetPath.value = joinPath(base, 'upload.bin')
  } finally {
    if (seq === browseSeq) loading.value = false
  }
}

function joinPath(dir: string, name: string) {
  const sep = dir.includes('\\') && !dir.includes('/') ? '\\' : '/'
  return dir.replace(/[\\/]+$/, '') + sep + name
}

function goUp() {
  const raw = path.value || roots.value[0] || ''
  const sep = raw.includes('\\') && !raw.includes('/') ? '\\' : '/'
  const parts = raw.replace(/[\\/]+$/, '').split(/[\\/]/)
  parts.pop()
  path.value = parts.length ? parts.join(sep) : roots.value[0] || ''
  browse()
}

function enter(row: Record<string, any>) {
  path.value = row.path ?? joinPath(path.value, row.name)
  browse()
}

async function viewFile(row: Record<string, any>) {
  const p = row.path ?? joinPath(path.value, row.name)
  const res = await api.files.read(p)
  editor.path = p
  editor.content = String((res as Record<string, any>).content ?? '')
  editor.visible = true
}

async function saveEditor() {
  await api.files.write(editor.path, editor.content)
  ElMessage.success('已写入')
  editor.visible = false
  browse()
}

function download(row: Record<string, any>) {
  const p = row.path ?? joinPath(path.value, row.name)
  downloadFile(`/api/files/download?path=${encodeURIComponent(p)}`, row.name || 'download')
}
async function removeEntry(row: Record<string, any>) {
  const p = row.path ?? joinPath(path.value, row.name)
  try {
    await ElMessageBox.confirm(`确认删除 ${p}？`, '删除', { type: 'warning' })
  } catch {
    return
  }
  await api.files.remove(p)
  ElMessage.success('已删除')
  browse()
}

function onPickFile(e: Event) {
  const files = (e.target as HTMLInputElement).files
  picked.value = files && files.length ? files[0] : null
  if (picked.value) targetPath.value = joinPath(path.value || roots.value[0] || '/', picked.value.name)
}

async function upload() {
  if (!picked.value) return
  uploading.value = true
  try {
    await api.files.upload(targetPath.value, picked.value)
    ElMessage.success('上传完成')
    browse()
  } catch {
    /* 拦截器已提示 */
  } finally {
    uploading.value = false
  }
}

async function createEmpty() {
  await api.files.write(targetPath.value, '')
  ElMessage.success('已创建')
  browse()
}

async function showBackups(row: Record<string, any>) {
  const p = row.path ?? joinPath(path.value, row.name)
  backupPath.value = p
  backupsLoading.value = true
  try {
    const res = await api.files.backups(p)
    backups.value = (Array.isArray(res) ? res : res.backups ?? []) as Record<string, any>[]
  } finally {
    backupsLoading.value = false
  }
}

async function diffBackup(row: Record<string, any>) {
  const res = await api.files.diff(backupPath.value, row.id)
  diffDlg.text = String((res as Record<string, any>).unified ?? (res as Record<string, any>).diff ?? JSON.stringify(res, null, 2))
  diffDlg.visible = true
}

async function restoreBackup(row: Record<string, any>) {
  try {
    await ElMessageBox.confirm('确认从该备份恢复？', '恢复', { type: 'warning' })
  } catch {
    return
  }
  await api.files.restore({ path: backupPath.value, backup_id: row.id })
  ElMessage.success('已恢复')
}

onMounted(async () => {
  try {
    const info = await api.files.roots()
    if (Array.isArray(info?.roots)) roots.value = (info as any).roots as string[]
    path.value = String(info?.default ?? roots.value[0] ?? '')
  } catch {
    /* 拦截器已提示 */
  }
  browse()
})
</script>
