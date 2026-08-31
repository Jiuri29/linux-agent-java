<template>
  <el-tabs v-model="tab">
    <el-tab-pane name="list">
      <template #label>
        资产列表<el-badge v-if="rows.length" :value="rows.length" :max="999" style="margin-left: 4px" />
      </template>
      <div class="toolbar">
        <el-input v-model="query.q" placeholder="关键字搜索" clearable style="width: 200px" @keyup.enter="load" />
        <el-select v-model="query.group" placeholder="分组" clearable style="width: 140px">
          <el-option v-for="g in groups" :key="g" :value="g" :label="g" />
        </el-select>
        <el-select v-model="query.tag" placeholder="标签" clearable style="width: 140px">
          <el-option v-for="t in tags" :key="t" :value="t" :label="t" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <div class="spacer" />
        <el-button type="success" :loading="syncing" @click="syncAssets">同步资产</el-button>
        <el-button type="primary" @click="openEdit()">新建资产</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" size="small" border>
        <el-table-column prop="id" label="ID" min-width="120" show-overflow-tooltip />
        <el-table-column prop="hostname" label="主机名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" min-width="120" />
        <el-table-column prop="group" label="分组" width="110" />
        <el-table-column label="标签" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="t in row.tags ?? []" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="os" label="操作系统" min-width="130" show-overflow-tooltip />
        <el-table-column prop="environment" label="环境" width="116">
          <template #default="{ row }">
            <span class="status-pill" :class="envPill(row.environment)">
              <i />{{ row.environment || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="showDetail(row)">详情</el-button>
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeAsset(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="目录维护（分组/标签/应用）" name="catalog">
      <el-row :gutter="16">
        <el-col v-for="col in catalogCols" :key="col.kind" :span="8">
          <el-card class="page-card" :header="col.title" shadow="never">
            <div class="toolbar">
              <el-input v-model="col.draft" size="small" :placeholder="`新增${col.title}`" @keyup.enter="addItem(col)" />
              <el-button size="small" type="primary" @click="addItem(col)">添加</el-button>
            </div>
            <el-tag
              v-for="name in col.items"
              :key="name"
              closable
              size="default"
              style="margin: 4px 6px 4px 0"
              @close="removeItem(col, name)"
            >{{ name }}</el-tag>
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>

    <el-tab-pane label="端口与依赖" name="relations">
      <el-row :gutter="16">
        <el-col :span="10">
          <el-card class="page-card" header="登记服务端口" shadow="never">
            <el-form label-width="80px" size="small">
              <el-form-item label="资产 ID">
                <el-select v-model="portForm.asset_id" filterable style="width: 100%">
                  <el-option v-for="a in rows" :key="a.id" :value="a.id" :label="a.hostname || a.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="端口">
                <el-input-number v-model="portForm.port" :min="1" :max="65535" />
              </el-form-item>
              <el-form-item label="协议">
                <el-select v-model="portForm.protocol" style="width: 120px">
                  <el-option value="tcp" /><el-option value="udp" />
                </el-select>
              </el-form-item>
              <el-form-item label="说明">
                <el-input v-model="portForm.description" />
              </el-form-item>
              <el-button type="primary" @click="addPort">提交</el-button>
            </el-form>
          </el-card>

          <el-card class="page-card" header="登记依赖边" shadow="never">
            <el-form label-width="80px" size="small">
              <el-form-item label="源资产">
                <el-select v-model="depForm.from_asset_id" filterable style="width: 100%">
                  <el-option v-for="a in rows" :key="a.id" :value="a.id" :label="a.hostname || a.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标资产">
                <el-select v-model="depForm.to_asset_id" filterable style="width: 100%">
                  <el-option v-for="a in rows" :key="a.id" :value="a.id" :label="a.hostname || a.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="类型">
                <el-input v-model="depForm.type" placeholder="如 http/db" />
              </el-form-item>
              <el-button type="primary" @click="addDep">提交</el-button>
            </el-form>
          </el-card>
        </el-col>

        <el-col :span="14">
          <el-card class="page-card" header="依赖关系" shadow="never">
            <el-table :data="deps" size="small" border>
              <el-table-column prop="from_asset_id" label="源" min-width="140" show-overflow-tooltip />
              <el-table-column prop="to_asset_id" label="目标" min-width="140" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="100" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>

    <el-tab-pane label="快照" name="snapshots">
      <div class="toolbar">
        <el-select v-model="snapAsset" placeholder="按资产过滤" clearable filterable style="width: 220px">
          <el-option v-for="a in rows" :key="a.id" :value="a.id" :label="a.hostname || a.id" />
        </el-select>
        <el-button @click="loadSnapshots">查询</el-button>
      </div>
      <pre class="output">{{ pretty(snapshots) }}</pre>
    </el-tab-pane>
  </el-tabs>

  <!-- 新建/编辑 -->
  <el-dialog v-model="edit.visible" :title="edit.mode === 'create' ? '新建资产' : '编辑资产'" width="560px">
    <el-form label-width="90px">
      <el-form-item label="ID">
        <el-input v-model="form.id" :disabled="edit.mode === 'edit'" placeholder="唯一标识，如 web-01" />
      </el-form-item>
      <el-form-item label="主机名"><el-input v-model="form.hostname" /></el-form-item>
      <el-form-item label="IP"><el-input v-model="form.ip" /></el-form-item>
      <el-form-item label="分组">
        <el-select v-model="form.group" clearable allow-create filterable style="width: 100%">
          <el-option v-for="g in groups" :key="g" :value="g" :label="g" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select v-model="form.tags" multiple filterable allow-create style="width: 100%">
          <el-option v-for="t in tags" :key="t" :value="t" :label="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属应用"><el-input v-model="form.application" /></el-form-item>
      <el-form-item label="操作系统"><el-input v-model="form.os" /></el-form-item>
      <el-form-item label="环境">
        <el-select v-model="form.environment" style="width: 160px">
          <el-option value="prod" /><el-option value="staging" /><el-option value="dev" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="edit.visible = false">取消</el-button>
      <el-button type="primary" @click="saveAsset">保存</el-button>
    </template>
  </el-dialog>

  <!-- 详情抽屉 -->
  <el-drawer v-model="detail.visible" title="资产详情" size="480px">
    <template v-if="detail.data">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item v-for="(v, k) in detail.data" :key="k" :label="String(k)">
          {{ Array.isArray(v) ? v.join(', ') : String(v ?? '-') }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const tab = ref('list')
const loading = ref(false)
const syncing = ref(false)
const rows = ref<Record<string, any>[]>([])
const groups = ref<string[]>([])
const tags = ref<string[]>([])
const apps = ref<string[]>([])
const deps = ref<Record<string, any>[]>([])
const snapshots = ref<unknown>([])
const snapAsset = ref('')
const query = reactive({ q: '', group: '', tag: '' })

type Kind = 'group' | 'tag' | 'application'
interface CatalogCol {
  kind: Kind
  title: string
  draft: string
  items: string[]
}
const catalogCols = reactive<CatalogCol[]>([
  { kind: 'group', title: '分组', draft: '', items: [] },
  { kind: 'tag', title: '标签', draft: '', items: [] },
  { kind: 'application', title: '应用', draft: '', items: [] },
])

const edit = reactive({ visible: false, mode: 'create' as 'create' | 'edit' })
const form = reactive({
  id: '', hostname: '', ip: '', group: '',
  tags: [] as string[], application: '', os: '', environment: 'prod',
})
const detail = reactive({ visible: false, data: null as Record<string, any> | null })

const portForm = reactive({ asset_id: '', port: 80, protocol: 'tcp', description: '' })
const depForm = reactive({ from_asset_id: '', to_asset_id: '', type: '' })

function pretty(v: unknown) {
  return JSON.stringify(v, null, 2)
}

function envPill(env: string): string {
  if (env === 'prod') return 'status-info'
  if (env === 'staging') return 'status-warn'
  if (env === 'dev') return 'status-neutral'
  return 'status-neutral'
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (query.q) params.q = query.q
    if (query.group) params.group = query.group
    if (query.tag) params.tag = query.tag
    const res = await api.assets.list(params)
    rows.value = (Array.isArray(res) ? res : res.assets ?? res.items ?? []) as Record<string, any>[]
    await loadCatalog()
    deps.value = await api.assets.dependencies()
  } finally {
    loading.value = false
  }
}

async function loadCatalog() {
  groups.value = await api.assets.groups()
  tags.value = await api.assets.tags()
  apps.value = await api.assets.applications()
  catalogCols[0].items = groups.value
  catalogCols[1].items = tags.value
  catalogCols[2].items = apps.value
}

function resetQuery() {
  query.q = ''
  query.group = ''
  query.tag = ''
  load()
}

function openEdit(row?: Record<string, any>) {
  edit.mode = row ? 'edit' : 'create'
  form.id = row?.id ?? ''
  form.hostname = row?.hostname ?? ''
  form.ip = row?.ip ?? row?.address ?? ''
  form.group = row?.group ?? ''
  form.tags = [...(row?.tags ?? [])]
  form.application = row?.application ?? ''
  form.os = row?.os ?? ''
  form.environment = row?.environment ?? 'prod'
  edit.visible = true
}

async function saveAsset() {
  if (!form.id) {
    ElMessage.warning('请填写资产 ID')
    return
  }
  const body: Record<string, unknown> = { ...form }
  if (edit.mode === 'edit') await api.assets.update(body)
  else await api.assets.create(body)
  ElMessage.success('已保存')
  edit.visible = false
  await load()
}

async function removeAsset(row: Record<string, any>) {
  await ElMessageBox.confirm(`确认删除资产「${row.hostname || row.id}」？`, '删除', { type: 'warning' })
  await api.assets.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

async function showDetail(row: Record<string, any>) {
  detail.data = await api.assets.detail(row.id)
  detail.visible = true
}

async function syncAssets() {
  syncing.value = true
  try {
    await api.assets.sync('manual')
    ElMessage.success('同步完成')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    syncing.value = false
  }
}

async function addItem(col: CatalogCol) {
  if (!col.draft) return
  if (col.kind === 'group') await api.assets.addGroup(col.draft)
  else if (col.kind === 'tag') await api.assets.addTag(col.draft)
  else await api.assets.addApplication(col.draft)
  col.draft = ''
  await loadCatalog()
}

async function removeItem(col: CatalogCol, name: string) {
  if (col.kind === 'group') await api.assets.deleteGroup(name)
  else if (col.kind === 'tag') await api.assets.deleteTag(name)
  await loadCatalog()
}

async function addPort() {
  if (!portForm.asset_id) {
    ElMessage.warning('请选择资产')
    return
  }
  await api.assets.addPorts({ ...portForm })
  ElMessage.success('已登记端口')
}

async function addDep() {
  if (!depForm.from_asset_id || !depForm.to_asset_id) {
    ElMessage.warning('请选择源与目标资产')
    return
  }
  await api.assets.addDependency({ ...depForm })
  deps.value = await api.assets.dependencies()
  ElMessage.success('已登记依赖')
}

async function loadSnapshots() {
  snapshots.value = await api.assets.snapshots(snapAsset.value ? { asset_id: snapAsset.value } : undefined)
}

onMounted(load)
</script>
