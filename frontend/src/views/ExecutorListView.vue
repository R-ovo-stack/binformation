<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteExecutor, listExecutors } from '@/api/executor'
import type { ExecutorDetail } from '@/types/executor'
import { EXECUTOR_KIND_OPTIONS } from '@/types/executor'
import { statusLabel } from '@/types/asset'
import AppNav from '@/components/AppNav.vue'

const router = useRouter()
const loading = ref(false)
const executors = ref<ExecutorDetail[]>([])

function kindLabel(kind: string) {
  return EXECUTOR_KIND_OPTIONS.find((o) => o.value === kind)?.label ?? kind
}

async function load() {
  loading.value = true
  try {
    executors.value = await listExecutors()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  void router.push({ name: 'executor-create' })
}

function openEdit(row: ExecutorDetail) {
  void router.push({ name: 'executor-edit', params: { id: String(row.id) } })
}

async function remove(row: ExecutorDetail) {
  try {
    await ElMessageBox.confirm(`确定删除程序/脚本「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteExecutor(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <AppNav />
    <header class="topbar">
      <div>
        <h1>程序 / 脚本</h1>
        <p class="meta">维护流向步骤与派生加工使用的执行程序或脚本。</p>
      </div>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新建</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="executors" stripe class="table" empty-text="暂无程序/脚本">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ kindLabel(row.kind) }}</template>
      </el-table-column>
      <el-table-column prop="defaultHostLabel" label="默认主机" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="owner" label="责任人" width="120" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 20px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

h1 {
  margin: 4px 0 0;
  font-size: 22px;
}

.meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
}

.table {
  background: var(--surface-solid);
  border-radius: var(--radius);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}
</style>
