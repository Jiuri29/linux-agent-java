<template>
  <el-tabs v-model="tab">
    <el-tab-pane label="用户管理" name="users">
      <div class="toolbar">
        <el-button type="primary" @click="openUser()">新建用户</el-button>
        <el-button @click="loadUsers">刷新</el-button>
        <div class="spacer" />
        <span class="muted">{{ users.length }} 个用户</span>
      </div>
      <el-table v-loading="usersLoading" :data="users" size="small" border>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="display_name" label="显示名称" min-width="120">
          <template #default="{ row }">{{ row.display_name || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles ?? []" :key="r" size="small" style="margin-right: 4px">{{ r }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'disabled' ? 'info' : 'success'" size="small">{{ row.status || 'active' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openUser(row)">编辑</el-button>
            <el-button size="small" text type="warning" @click="resetPwd(row)">重置密码</el-button>
            <el-button size="small" text type="danger" @click="toggleStatus(row)">
              {{ row.status === 'disabled' ? '启用' : '禁用' }}
            </el-button>
            <el-button size="small" text type="danger" @click="revoke(row)">踢下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="角色管理" name="roles">
      <div class="toolbar">
        <el-button type="primary" @click="openRole()">新建角色</el-button>
        <el-button @click="loadRoles">刷新</el-button>
      </div>
      <el-table v-loading="rolesLoading" :data="roles" size="small" border>
        <el-table-column prop="name" label="角色" min-width="140" />
        <el-table-column label="权限点" min-width="420">
          <template #default="{ row }">
            <el-tag v-for="p in row.permissions ?? []" :key="p" size="small" type="info" effect="plain"
              style="margin: 2px 4px 2px 0">{{ p }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内置" width="80">
          <template #default="{ row }">{{ row.builtin ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openRole(row)">编辑</el-button>
            <el-button size="small" text type="danger" :disabled="row.builtin" @click="removeRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="dlg.visible" :title="dlg.title" width="480px">
    <el-form label-width="90px">
      <template v-if="dlg.kind === 'user'">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="dlg.mode === 'edit'" />
        </el-form-item>
        <el-form-item v-if="dlg.mode === 'create'" label="密码">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.roles" multiple style="width: 100%">
            <el-option v-for="r in roleNames" :key="r" :value="r" :label="r" />
          </el-select>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="角色名">
          <el-input v-model="roleForm.name" />
        </el-form-item>
        <el-form-item label="权限点">
          <el-select v-model="roleForm.permissions" multiple filterable allow-create style="width: 100%">
            <el-option v-for="p in permissionOptions" :key="p" :value="p" :label="p" />
          </el-select>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="dlg.visible = false">取消</el-button>
      <el-button type="primary" @click="saveDlg">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const tab = ref('users')
const users = ref<Record<string, any>[]>([])
const usersLoading = ref(false)
const roles = ref<Record<string, any>[]>([])
const rolesLoading = ref(false)
const roleNames = computed(() => roles.value.map((r) => String(r.name)))
const permissionOptions = [
  'assets:read', 'assets:write', 'monitor:read', 'monitor:write', 'services:read',
  'services:write', 'terminal:run', 'files:read', 'files:write', 'guardrails:read',
  'guardrails:write', 'chatops:use', 'mcp:read', 'mcp:write', 'audit:read',
  'audit:admin', 'users:read', 'users:write', 'tests:run', 'platform:admin',
]

const dlg = reactive({
  visible: false,
  kind: 'user' as 'user' | 'role',
  mode: 'create' as 'create' | 'edit',
  title: '',
})
const userForm = reactive({ username: '', password: '', displayName: '', roles: [] as string[] })
const roleForm = reactive({ name: '', permissions: [] as string[] })

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await api.users.list()
  } finally {
    usersLoading.value = false
  }
}
async function loadRoles() {
  rolesLoading.value = true
  try {
    roles.value = await api.users.roles()
  } finally {
    rolesLoading.value = false
  }
}

function openUser(row?: Record<string, any>) {
  dlg.kind = 'user'
  dlg.mode = row ? 'edit' : 'create'
  dlg.title = row ? `编辑用户：${row.username}` : '新建用户'
  userForm.username = row?.username ?? ''
  userForm.password = ''
  userForm.displayName = row?.display_name ?? ''
  userForm.roles = [...(row?.roles ?? [])]
  dlg.visible = true
}

function openRole(row?: Record<string, any>) {
  dlg.kind = 'role'
  dlg.mode = row ? 'edit' : 'create'
  dlg.title = row ? `编辑角色：${row.name}` : '新建角色'
  roleForm.name = row?.name ?? ''
  roleForm.permissions = [...(row?.permissions ?? [])]
  dlg.visible = true
}

async function saveDlg() {
  try {
    if (dlg.kind === 'user') {
      if (dlg.mode === 'create') {
        await api.users.create({
          username: userForm.username,
          password: userForm.password,
          display_name: userForm.displayName || undefined,
        })
        if (userForm.roles.length) {
          await api.users.update({ username: userForm.username, roles: userForm.roles })
        }
      } else {
        await api.users.update({
          username: userForm.username,
          display_name: userForm.displayName || undefined,
          roles: userForm.roles,
        })
      }
      await loadUsers()
    } else {
      await api.users.upsertRole(roleForm.name, roleForm.permissions)
      await loadRoles()
    }
    ElMessage.success('保存成功')
    dlg.visible = false
  } catch {
    /* 拦截器已提示 */
  }
}

async function resetPwd(row: Record<string, any>) {
  const { value } = await ElMessageBox.prompt(`为 ${row.username} 设置新密码`, '重置密码', {
    inputType: 'password',
    inputPattern: /^.{6,}$/,
    inputErrorMessage: '密码至少 6 位',
  })
  await api.users.resetPassword(row.username, value)
  ElMessage.success('已重置')
}

async function toggleStatus(row: Record<string, any>) {
  const next = row.status === 'disabled' ? 'active' : 'disabled'
  await api.users.setStatus(row.username, next)
  ElMessage.success(next === 'disabled' ? '已禁用' : '已启用')
  await loadUsers()
}

async function revoke(row: Record<string, any>) {
  const res = await api.users.revokeSessions(row.username)
  ElMessage.success(`已吊销 ${res.revoked ?? 0} 个会话`)
}

async function removeRole(row: Record<string, any>) {
  await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '删除角色', { type: 'warning' })
  await api.users.deleteRole(row.name)
  ElMessage.success('已删除')
  await loadRoles()
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<style scoped>
.muted {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
