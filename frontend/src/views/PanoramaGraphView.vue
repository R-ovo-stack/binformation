<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listAssets } from '@/api/asset'
import { getPanoramaGraph, getTechnicalPanoramaGraph } from '@/api/panorama'
import AppNav from '@/components/AppNav.vue'
import EdgeDetailPanel from '@/components/EdgeDetailPanel.vue'
import FlowGraphCanvas from '@/components/FlowGraphCanvas.vue'
import PanoramaGraphCanvas from '@/components/PanoramaGraphCanvas.vue'
import type { PanoramaEdge, PanoramaGraph } from '@/types/panorama'
import { dataTypeLabel } from '@/types/panorama'
import type { AssetGraph, DataAsset, GraphEdge } from '@/types/graph'
import type { LayoutMode } from '@/utils/graphLayout'

const router = useRouter()
const activeTab = ref<'lineage' | 'technical'>('lineage')
const loading = ref(false)

const lineageGraph = ref<PanoramaGraph | null>(null)
const includeEndpointLinks = ref(true)
const selectedAssetId = ref<number | null>(null)
const selectedLineageEdge = ref<PanoramaEdge | null>(null)
const lineageCanvasRef = ref<InstanceType<typeof PanoramaGraphCanvas> | null>(null)

const technicalGraph = ref<AssetGraph | null>(null)
const allAssets = ref<DataAsset[]>([])
const selectedAssetIds = ref<number[]>([])
const includeAuxiliary = ref(false)
const includeDerivationBridges = ref(true)
const layoutMode = ref<LayoutMode>('compact')
const selectedFlowEdge = ref<GraphEdge | null>(null)
const technicalCanvasRef = ref<InstanceType<typeof FlowGraphCanvas> | null>(null)

const detailAssetId = computed(() => {
  if (selectedFlowEdge.value?.fromAssetId) return selectedFlowEdge.value.fromAssetId
  return null
})

async function loadLineage() {
  loading.value = true
  selectedAssetId.value = null
  selectedLineageEdge.value = null
  try {
    lineageGraph.value = await getPanoramaGraph(includeEndpointLinks.value)
  } catch (e) {
    lineageGraph.value = null
    ElMessage.error(e instanceof Error ? e.message : '加载血缘全景失败')
  } finally {
    loading.value = false
  }
}

async function loadTechnical() {
  loading.value = true
  selectedFlowEdge.value = null
  try {
    technicalGraph.value = await getTechnicalPanoramaGraph({
      assetIds: selectedAssetIds.value.length ? selectedAssetIds.value : undefined,
      includeAuxiliary: includeAuxiliary.value,
      includeDerivationBridges: includeDerivationBridges.value,
    })
  } catch (e) {
    technicalGraph.value = null
    ElMessage.error(e instanceof Error ? e.message : '加载技术全景失败')
  } finally {
    loading.value = false
  }
}

async function loadAssets() {
  try {
    allAssets.value = await listAssets()
  } catch {
    allAssets.value = []
  }
}

function loadActive() {
  if (activeTab.value === 'lineage') void loadLineage()
  else void loadTechnical()
}

function openAssetGraph(assetId: number) {
  void router.push({ name: 'asset-graph', params: { id: String(assetId) } })
}

function openAssetFlows(assetId: number) {
  void router.push({ name: 'asset-flows', params: { id: String(assetId) } })
}

function openGuide() {
  void router.push({ name: 'flow-editing-guide' })
}

function zoomToFit() {
  if (activeTab.value === 'lineage') lineageCanvasRef.value?.zoomToFit()
  else technicalCanvasRef.value?.zoomToFit()
}

function zoomIn() {
  if (activeTab.value === 'lineage') lineageCanvasRef.value?.zoomIn()
  else technicalCanvasRef.value?.zoomIn()
}

function zoomOut() {
  if (activeTab.value === 'lineage') lineageCanvasRef.value?.zoomOut()
  else technicalCanvasRef.value?.zoomOut()
}

watch(activeTab, () => loadActive())

onMounted(async () => {
  await loadAssets()
  await loadLineage()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <AppNav />

    <header class="topbar">
      <div>
        <el-button link @click="router.push('/')">← 返回资产列表</el-button>
        <h1>资产全景图</h1>
        <p class="meta">
          血缘视角看资产间关系；技术全景合并多资产落点级流向（与单资产成图一致）
        </p>
      </div>
      <div class="actions">
        <el-button link type="primary" @click="openGuide">流向配置说明</el-button>
        <el-button @click="zoomOut">缩小</el-button>
        <el-button @click="zoomIn">放大</el-button>
        <el-button @click="zoomToFit">适配</el-button>
        <el-button :loading="loading" @click="loadActive">刷新</el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="panorama-tabs">
      <el-tab-pane label="血缘视角" name="lineage">
        <div class="toolbar card">
          <el-switch v-model="includeEndpointLinks" active-text="含落点衔接" @change="loadLineage" />
          <span class="tip" v-if="lineageGraph">
            共 {{ lineageGraph.assetCount }} 个资产 · {{ lineageGraph.edgeCount }} 条跨资产关系
          </span>
        </div>

        <div class="workspace">
          <PanoramaGraphCanvas
            ref="lineageCanvasRef"
            class="canvas-panel"
            :graph="lineageGraph"
            :selected-asset-id="selectedAssetId"
            @select-asset="selectedAssetId = $event; selectedLineageEdge = null"
            @select-edge="selectedLineageEdge = $event; selectedAssetId = null"
          />

          <aside class="side card">
            <template v-if="selectedAssetId && lineageGraph">
              <h2>资产</h2>
              <template v-for="n in lineageGraph.nodes" :key="n.assetId">
                <dl v-if="n.assetId === selectedAssetId" class="detail">
                  <div><dt>名称</dt><dd>{{ n.name }}</dd></div>
                  <div><dt>编码</dt><dd>{{ n.code }}</dd></div>
                  <div><dt>类型</dt><dd>{{ dataTypeLabel(n.dataType) }}</dd></div>
                  <div><dt>主流向</dt><dd>{{ n.primaryFlowCount }} 条</dd></div>
                </dl>
              </template>
              <div class="side-actions">
                <el-button type="primary" @click="openAssetGraph(selectedAssetId)">一键成图</el-button>
                <el-button @click="openAssetFlows(selectedAssetId)">管理流向</el-button>
              </div>
            </template>

            <template v-else-if="selectedLineageEdge">
              <h2>跨资产关系</h2>
              <dl class="detail">
                <div>
                  <dt>类型</dt>
                  <dd>{{ selectedLineageEdge.type === 'DERIVE' ? '派生' : '落点衔接' }}</dd>
                </div>
                <div><dt>说明</dt><dd>{{ selectedLineageEdge.label }}</dd></div>
              </dl>
              <div class="side-actions">
                <el-button @click="openAssetGraph(selectedLineageEdge.sourceAssetId)">上游成图</el-button>
                <el-button type="primary" @click="openAssetGraph(selectedLineageEdge.targetAssetId)">
                  下游成图
                </el-button>
              </div>
            </template>

            <template v-else>
              <p class="help">点击资产卡片或连线查看详情；无连线的资产在画布下方。</p>
            </template>
          </aside>
        </div>
      </el-tab-pane>

      <el-tab-pane label="技术全景" name="technical">
        <div class="toolbar card technical-toolbar">
          <el-select
            v-model="selectedAssetIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="全部资产（可多选筛选）"
            class="asset-filter"
            @change="loadTechnical"
          >
            <el-option
              v-for="a in allAssets"
              :key="a.id"
              :label="`${a.name} (${a.code})`"
              :value="a.id"
            />
          </el-select>
          <el-switch v-model="includeAuxiliary" active-text="含辅助" @change="loadTechnical" />
          <el-switch
            v-model="includeDerivationBridges"
            active-text="含派生桥接"
            @change="loadTechnical"
          />
          <el-radio-group v-model="layoutMode" size="small">
            <el-radio-button label="compact">简洁</el-radio-button>
            <el-radio-button label="full">完整</el-radio-button>
          </el-radio-group>
          <span class="tip" v-if="technicalGraph">
            节点 {{ technicalGraph.nodes.length }} · 流向 {{ technicalGraph.edges.length }}
          </span>
        </div>

        <p class="mode-tip">
          合并所选资产的全部主流向；边标签含所属资产名。未选资产时默认包含全部。
        </p>

        <div class="workspace">
          <FlowGraphCanvas
            ref="technicalCanvasRef"
            :key="`${layoutMode}-${selectedAssetIds.join(',')}-${includeAuxiliary}`"
            class="canvas-panel"
            :graph="technicalGraph"
            :layout-mode="layoutMode"
            @select-edge="selectedFlowEdge = $event"
          />

          <EdgeDetailPanel
            class="side card"
            :edge="selectedFlowEdge"
            :asset-id="detailAssetId"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  align-items: flex-end;
  margin-bottom: 12px;
}

h1 {
  margin: 4px 0 0;
  font-size: 22px;
}

.meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  max-width: 640px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.panorama-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.card {
  background: var(--surface-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-panel);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.technical-toolbar .asset-filter {
  min-width: 240px;
  max-width: 420px;
}

.tip {
  font-size: 13px;
  color: var(--ink-soft);
}

.mode-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--muted-soft);
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 12px;
  min-height: 560px;
}

.canvas-panel {
  min-height: 560px;
}

.side {
  padding: 14px;
  overflow: auto;
  min-height: 560px;
}

.side h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.detail {
  margin: 0 0 12px;
  display: grid;
  gap: 8px;
}

.detail dt {
  font-size: 12px;
  color: var(--muted-soft);
}

.detail dd {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--ink-soft);
  word-break: break-all;
}

.side-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.help {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.6;
}

@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}
</style>
