<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listAssets } from '@/api/asset'
import type { DataAsset } from '@/types/graph'

const router = useRouter()
const loading = ref(false)
const assets = ref<DataAsset[]>([])

async function load() {
  loading.value = true
  try {
    assets.value = await listAssets()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载资产失败')
  } finally {
    loading.value = false
  }
}

function openGraph(row: DataAsset) {
  void router.push({ name: 'asset-graph', params: { id: String(row.id) } })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="hero">
      <div>
        <p class="brand">数据中心台账</p>
        <h1>数据资产</h1>
        <p class="sub">选择资产，一键生成流向图。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-table
      v-loading="loading"
      :data="assets"
      stripe
      class="table"
      empty-text="暂无资产，请先启动后端并确认样例数据已加载"
      @row-click="openGraph"
    >
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column prop="dataType" label="类型" width="120" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="owner" label="责任人" width="120" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openGraph(row)">一键成图</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 28px 20px 48px;
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 20px;
}

.brand {
  margin: 0 0 6px;
  font-family: 'Fraunces', 'IBM Plex Serif', serif;
  font-size: 28px;
  font-weight: 600;
  color: #0f3d36;
  letter-spacing: 0.02em;
}

h1 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.sub {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
}

.table {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
