<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRawFile } from 'element-plus'
import { listAssets } from '@/api/asset'
import { downloadFullLedgerExport } from '@/api/export'
import { downloadEndpointImportTemplate, importEndpointsFromCsv } from '@/api/endpoint'
import type { EndpointImportResult } from '@/types/endpointImport'
import type { DataAsset } from '@/types/graph'
import { dataTypeLabel, statusLabel } from '@/types/asset'
import AppNav from '@/components/AppNav.vue'

const router = useRouter()
const loading = ref(false)
const exportingJson = ref(false)
const exportingZip = ref(false)
const downloadingTemplate = ref(false)
const importingEndpoints = ref(false)
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

function openFlows(row: DataAsset) {
  void router.push({ name: 'asset-flows', params: { id: String(row.id) } })
}

function openEdit(row: DataAsset) {
  void router.push({ name: 'asset-edit', params: { id: String(row.id) } })
}

function openCreate() {
  void router.push({ name: 'asset-create' })
}

async function exportLedger(format: 'json' | 'zip') {
  const exporting = format === 'json' ? exportingJson : exportingZip
  exporting.value = true
  try {
    await downloadFullLedgerExport(format)
    ElMessage.success(format === 'json' ? 'JSON 全量导出已开始下载' : 'CSV 压缩包导出已开始下载')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

async function downloadTemplate() {
  downloadingTemplate.value = true
  try {
    await downloadEndpointImportTemplate()
    ElMessage.success('落点导入模板已开始下载')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '模板下载失败')
  } finally {
    downloadingTemplate.value = false
  }
}

function formatImportSummary(result: EndpointImportResult): string {
  const lines = [
    `共 ${result.totalRows} 行，成功 ${result.created} 条，跳过 ${result.skipped} 条。`,
  ]
  if (result.errors.length > 0) {
    lines.push('', '明细：')
    for (const err of result.errors.slice(0, 8)) {
      lines.push(`第 ${err.row} 行「${err.name || '-'}」：${err.message}`)
    }
    if (result.errors.length > 8) {
      lines.push(`… 另有 ${result.errors.length - 8} 条`)
    }
  }
  return lines.join('\n')
}

async function handleEndpointImport(file: File) {
  importingEndpoints.value = true
  try {
    const result = await importEndpointsFromCsv(file)
    await ElMessageBox.alert(formatImportSummary(result), '落点导入完成', {
      confirmButtonText: '知道了',
      type: result.created > 0 ? 'success' : result.errors.length > 0 ? 'warning' : 'info',
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '落点导入失败')
  } finally {
    importingEndpoints.value = false
  }
}

function onEndpointImportSelect(file: UploadRawFile) {
  void handleEndpointImport(file as File)
  return false
}

onMounted(load)
</script>

<template>
  <div class="page">
    <AppNav />
    <header class="page-hero">
      <div>
        <p class="page-brand">数据中心台账</p>
        <h1 class="page-title">数据资产</h1>
        <p class="page-sub">维护数据资产、落点与流向，或一键生成流向图。</p>
      </div>
      <div class="page-actions">
        <el-button @click="router.push('/panorama')">资产全景图</el-button>
        <el-button @click="router.push('/lineage')">供需查询</el-button>
        <el-button :loading="exportingJson" @click="exportLedger('json')">导出 JSON</el-button>
        <el-button :loading="exportingZip" @click="exportLedger('zip')">导出 CSV 包</el-button>
        <el-button type="primary" @click="openCreate">新建资产</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <section class="import-card page-panel">
      <div>
        <h2>落点导入</h2>
        <p class="import-sub">下载 CSV 模板，按 parentPath 填写层级后批量导入全部落点。</p>
      </div>
      <div class="import-actions">
        <el-button :loading="downloadingTemplate" @click="downloadTemplate">下载导入模板</el-button>
        <el-upload
          :show-file-list="false"
          accept=".csv,text/csv"
          :disabled="importingEndpoints"
          :before-upload="onEndpointImportSelect"
        >
          <el-button type="primary" :loading="importingEndpoints">上传 CSV 导入</el-button>
        </el-upload>
      </div>
    </section>

    <el-table
      v-loading="loading"
      :data="assets"
      stripe
      class="page-table"
      empty-text="暂无资产，请先启动后端并确认样例数据已加载"
      @row-click="openGraph"
    >
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ dataTypeLabel(row.dataType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="owner" label="责任人" width="120" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click.stop="openFlows(row)">管理流向</el-button>
          <el-button
            link
            type="primary"
            @click.stop="router.push({ name: 'asset-derivations', params: { id: String(row.id) } })"
          >
            派生
          </el-button>
          <el-button link type="primary" @click.stop="openGraph(row)">一键成图</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.import-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 18px 20px;
  flex-wrap: wrap;
  background:
    linear-gradient(135deg, rgba(13, 148, 136, 0.08), transparent 42%),
    var(--surface-raised);
}

.import-card h2 {
  margin: 0;
  font-size: 1rem;
  font-weight: 650;
}

.import-sub {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.import-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
