<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAssetGraph } from '@/api/asset'
import { saveAssetLayout } from '@/api/layout'
import type { AssetGraph, GraphEdge } from '@/types/graph'
import type { LayoutMode } from '@/utils/graphLayout'
import FlowGraphCanvas from '@/components/FlowGraphCanvas.vue'
import EdgeDetailPanel from '@/components/EdgeDetailPanel.vue'

const props = defineProps<{
  id: string
}>()

const router = useRouter()
const loading = ref(false)
const savingLayout = ref(false)
const includeAuxiliary = ref(false)
const includeUpstream = ref(false)
const layoutMode = ref<LayoutMode>('compact')
const compressExecutorHost = ref(false)
const graph = ref<AssetGraph | null>(null)
const selectedEdge = ref<GraphEdge | null>(null)
const canvasRef = ref<InstanceType<typeof FlowGraphCanvas> | null>(null)

const assetId = computed(() => Number(props.id))
const showUpstreamToggle = computed(() => Boolean(graph.value?.hasUpstream))

function openFlows() {
  void router.push({ name: 'asset-flows', params: { id: props.id } })
}

async function loadGraph() {
  if (!Number.isFinite(assetId.value)) {
    ElMessage.error('无效的资产 ID')
    return
  }
  loading.value = true
  selectedEdge.value = null
  try {
    graph.value = await getAssetGraph(
      assetId.value,
      includeAuxiliary.value,
      includeUpstream.value,
    )
    if (!graph.value?.hasUpstream) {
      includeUpstream.value = false
    }
  } catch (e) {
    graph.value = null
    ElMessage.error(e instanceof Error ? e.message : '加载流向图失败')
  } finally {
    loading.value = false
  }
}

function onSelectEdge(edge: GraphEdge | null) {
  selectedEdge.value = edge
}

function regenerate() {
  void loadGraph()
}

function zoomToFit() {
  canvasRef.value?.zoomToFit()
}

function zoomIn() {
  canvasRef.value?.zoomIn()
}

function zoomOut() {
  canvasRef.value?.zoomOut()
}

function exportPng() {
  canvasRef.value?.exportPng()
}

async function saveLayout() {
  const nodes = canvasRef.value?.collectEndpointLayouts() ?? []
  if (!nodes.length) {
    ElMessage.warning('当前画布没有可保存的落点位置')
    return
  }
  savingLayout.value = true
  try {
    await saveAssetLayout(assetId.value, nodes)
    ElMessage.success(`已保存 ${nodes.length} 个落点布局`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存布局失败')
  } finally {
    savingLayout.value = false
  }
}

function openDerivations() {
  void router.push({ name: 'asset-derivations', params: { id: props.id } })
}

function openDerivationEdit(derivationId: number) {
  void router.push({
    name: 'derivation-edit',
    params: { id: props.id, derivationId: String(derivationId) },
  })
}

watch(
  () => props.id,
  () => {
    includeUpstream.value = false
    void loadGraph()
  },
)

onMounted(() => {
  void loadGraph()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="router.push('/')">← 返回资产列表</el-button>
        <h1>{{ graph?.assetName || '流向图' }}</h1>
        <p class="meta">
          <span v-if="graph">{{ graph.assetCode }} · {{ graph.dataType }}</span>
          <span v-if="graph"> · 节点 {{ graph.nodes.length }} · 流向 {{ graph.edges.length }}</span>
        </p>
      </div>
      <div class="actions">
        <el-switch
          v-model="includeAuxiliary"
          inline-prompt
          active-text="含辅助"
          inactive-text="主流向"
          @change="regenerate"
        />
        <el-switch
          v-if="showUpstreamToggle"
          v-model="includeUpstream"
          inline-prompt
          active-text="含前置"
          inactive-text="仅本资产"
          @change="regenerate"
        />
        <el-radio-group v-model="layoutMode" size="small">
          <el-radio-button value="compact">简洁</el-radio-button>
          <el-radio-button value="full">完整</el-radio-button>
        </el-radio-group>
        <el-switch
          v-model="compressExecutorHost"
          inline-prompt
          active-text="压缩部署"
          inactive-text="展开部署"
        />
        <el-button @click="openFlows">管理流向</el-button>
        <el-button @click="openDerivations">管理派生</el-button>
        <el-button :loading="savingLayout" @click="saveLayout">保存布局</el-button>
        <el-button type="primary" :loading="loading" @click="regenerate">一键成图</el-button>
        <el-button @click="zoomOut">缩小</el-button>
        <el-button @click="zoomIn">放大</el-button>
        <el-button @click="zoomToFit">适配</el-button>
        <el-button class="desktop-only" @click="exportPng">导出 PNG</el-button>
      </div>
    </header>

    <p class="mobile-tip">
      完整模式：Kafka 集群卡片内主题/节点不可单独拖动，只能拖整卡。简洁/完整均以卡片表达主题归属 Kafka。
      「压缩部署」开启后不画程序→主机部署连线，主机名写入程序框。派生输出资产可开「含前置」。
    </p>

    <div class="workspace">
      <FlowGraphCanvas
        ref="canvasRef"
        class="canvas"
        :graph="graph"
        :layout-mode="layoutMode"
        :compress-executor-host="compressExecutorHost"
        @select-edge="onSelectEdge"
      />
      <EdgeDetailPanel class="side" :edge="selectedEdge" :asset-id="assetId" />
    </div>

    <section v-if="graph?.derivations?.length" class="derivations">
      <h2>相关派生/加工</h2>
      <el-table :data="graph.derivations" size="small">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="outputAssetName" label="输出资产" min-width="120" />
        <el-table-column label="输入资产" min-width="180">
          <template #default="{ row }">
            {{ row.inputs.map((i: { assetName?: string | null }) => i.assetName).filter(Boolean).join('、') || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="executorName" label="程序/脚本" min-width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDerivationEdit(row.derivationId)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.page {
  height: 100%;
  min-height: 100vh;
  padding: 14px clamp(10px, 1.2vw, 16px) 18px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

h1 {
  margin: 4px 0 0;
  font-size: 22px;
  color: var(--ink);
}

.meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.workspace {
  flex: 1;
  min-height: 560px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 12px;
}

.canvas,
.side {
  min-height: 560px;
}

.derivations {
  background: var(--surface-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-panel);
  padding: 12px 14px 16px;
  backdrop-filter: blur(8px);
}

.derivations h2 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--ink);
}

.mobile-tip {
  display: none;
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 960px) {
  .page {
    padding: 12px;
    gap: 10px;
  }

  .workspace {
    grid-template-columns: 1fr;
    min-height: 70vh;
  }

  .canvas {
    min-height: 70vh;
  }

  .side {
    min-height: 180px;
  }

  .mobile-tip {
    display: block;
  }

  .desktop-only {
    display: none;
  }

  h1 {
    font-size: 18px;
  }
}
</style>
