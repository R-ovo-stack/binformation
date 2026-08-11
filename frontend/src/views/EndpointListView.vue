<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteEndpoint, getEndpointTypeMeta, listEndpoints } from '@/api/endpoint'
import { typeLabel, type EndpointDetail } from '@/types/endpoint'

const router = useRouter()
const loading = ref(false)
const endpoints = ref<EndpointDetail[]>([])
const typeFilter = ref('')
const typeLabels = ref<Record<string, string>>({})
const typeOptions = ref<string[]>([])

const filtered = computed(() => {
  if (!typeFilter.value) return endpoints.value
  return endpoints.value.filter((e) => e.type === typeFilter.value)
})

async function load() {
  loading.value = true
  try {
    const [meta, list] = await Promise.all([
      getEndpointTypeMeta(),
      listEndpoints(typeFilter.value || undefined),
    ])
    typeLabels.value = meta.labels
    typeOptions.value = meta.types
    endpoints.value = list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  void router.push({ name: 'endpoint-create' })
}

function openEdit(row: EndpointDetail) {
  void router.push({ name: 'endpoint-edit', params: { id: String(row.id) } })
}

async function remove(row: EndpointDetail) {
  try {
    await ElMessageBox.confirm(`确定删除落点「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteEndpoint(row.id)
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
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="router.push('/')">← 返回资产列表</el-button>
        <h1>落点管理</h1>
        <p class="meta">维护安全区、系统、Kafka 主题、目录等拓扑节点</p>
      </div>
      <div class="actions">
        <el-select
          v-model="typeFilter"
          clearable
          placeholder="按类型筛选"
          style="width: 180px"
          @change="load"
        >
          <el-option
            v-for="t in typeOptions"
            :key="t"
            :label="typeLabel(t, typeLabels)"
            :value="t"
          />
        </el-select>
        <el-button type="primary" @click="openCreate">新建落点</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-table :data="filtered" stripe empty-text="暂无落点">
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ typeLabel(row.type, typeLabels) }}</template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="breadcrumb" label="归属链" min-width="220" show-overflow-tooltip />
      <el-table-column prop="zoneName" label="安全区" width="100" />
      <el-table-column prop="status" label="状态" width="96" />
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
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px 20px 48px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

h1 {
  margin: 4px 0 0;
  font-size: 22px;
}

.meta {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
