<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAsset } from '@/api/asset'
import { deleteFlow, listFlowsByAsset } from '@/api/flow'
import type { DataAsset } from '@/types/graph'
import { purposeLabel, type FlowSummary } from '@/types/flow'

const props = defineProps<{ id: string }>()
const router = useRouter()
const loading = ref(false)
const asset = ref<DataAsset | null>(null)
const flows = ref<FlowSummary[]>([])

const assetId = computed(() => Number(props.id))

async function load() {
  if (!Number.isFinite(assetId.value)) {
    ElMessage.error('无效的资产 ID')
    return
  }
  loading.value = true
  try {
    const [assetData, flowList] = await Promise.all([
      getAsset(assetId.value),
      listFlowsByAsset(assetId.value),
    ])
    asset.value = assetData
    flows.value = flowList
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载流向失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  void router.push({ name: 'flow-create', params: { id: props.id } })
}

function openEdit(row: FlowSummary) {
  void router.push({ name: 'flow-edit', params: { id: props.id, flowId: String(row.id) } })
}

function openGraph() {
  void router.push({ name: 'asset-graph', params: { id: props.id } })
}

async function remove(row: FlowSummary) {
  try {
    await ElMessageBox.confirm(`确定删除流向 #${row.id} 吗？路径与步骤将一并删除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteFlow(row.id)
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
        <h1>{{ asset?.name || '流向管理' }}</h1>
        <p class="meta">
          <span v-if="asset">{{ asset.code }} · {{ asset.dataType }}</span>
          <span v-if="asset"> · 共 {{ flows.length }} 条流向</span>
        </p>
      </div>
      <div class="actions">
        <el-button @click="openGraph">一键成图</el-button>
        <el-button type="primary" @click="openCreate">新建流向</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-table :data="flows" stripe empty-text="暂无流向，点击「新建流向」开始维护">
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column label="源 → 目标" min-width="280">
        <template #default="{ row }">
          <div class="ep">{{ row.sourceEndpointLabel }}</div>
          <div class="arrow">→</div>
          <div class="ep">{{ row.targetEndpointLabel }}</div>
        </template>
      </el-table-column>
      <el-table-column label="用途" width="100">
        <template #default="{ row }">{{ purposeLabel(row.purpose) }}</template>
      </el-table-column>
      <el-table-column label="主流向" width="88">
        <template #default="{ row }">
          <el-tag :type="row.primary ? 'success' : 'info'" size="small">
            {{ row.primary ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="96" />
      <el-table-column label="路径/步骤" width="110">
        <template #default="{ row }">{{ row.pathCount }} / {{ row.stepCount }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
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
  color: #0f172a;
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

.ep {
  font-size: 12px;
  color: #334155;
  line-height: 1.4;
}

.arrow {
  color: #94a3b8;
  font-size: 12px;
  margin: 2px 0;
}
</style>
