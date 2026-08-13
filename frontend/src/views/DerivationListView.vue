<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAsset } from '@/api/asset'
import { deleteDerivation, listDerivationsByAsset } from '@/api/derivation'
import type { DataAsset } from '@/types/graph'
import type { DerivationDetail } from '@/types/derivation'
import { dataTypeLabel, statusLabel } from '@/types/asset'

const props = defineProps<{ id: string }>()
const router = useRouter()
const loading = ref(false)
const asset = ref<DataAsset | null>(null)
const derivations = ref<DerivationDetail[]>([])

const assetId = computed(() => Number(props.id))

async function load() {
  if (!Number.isFinite(assetId.value)) {
    ElMessage.error('无效的资产 ID')
    return
  }
  loading.value = true
  try {
    const [assetData, list] = await Promise.all([
      getAsset(assetId.value),
      listDerivationsByAsset(assetId.value),
    ])
    asset.value = assetData
    derivations.value = list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  void router.push({ name: 'derivation-create', params: { id: props.id } })
}

function openEdit(row: DerivationDetail) {
  void router.push({ name: 'derivation-edit', params: { id: props.id, derivationId: String(row.id) } })
}

function openGraph() {
  void router.push({ name: 'asset-graph', params: { id: props.id } })
}

async function remove(row: DerivationDetail) {
  try {
    await ElMessageBox.confirm(`确定删除派生「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteDerivation(row.id)
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
        <el-button link @click="router.push({ name: 'asset-edit', params: { id: props.id } })">
          ← 返回资产编辑
        </el-button>
        <h1>{{ asset?.name || '派生/加工' }}</h1>
        <p class="meta">
          <span v-if="asset">{{ asset.code }} · {{ dataTypeLabel(asset.dataType) }}</span>
          <span v-if="asset"> · 共 {{ derivations.length }} 条派生</span>
        </p>
      </div>
      <div class="actions">
        <el-button @click="openGraph">一键成图</el-button>
        <el-button type="primary" @click="openCreate">新建派生</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-table :data="derivations" stripe class="table" empty-text="暂无派生/加工定义">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="输入资产" min-width="200">
        <template #default="{ row }">
          {{
            row.inputs
              .map((i: { inputAssetName?: string | null }) => i.inputAssetName)
              .filter(Boolean)
              .join('、') || '-'
          }}
        </template>
      </el-table-column>
      <el-table-column prop="executorName" label="程序/脚本" min-width="140" />
      <el-table-column prop="hostLabel" label="执行主机" min-width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
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
}
</style>
