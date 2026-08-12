<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPanoramaGraph } from '@/api/panorama'
import AppNav from '@/components/AppNav.vue'
import PanoramaGraphCanvas from '@/components/PanoramaGraphCanvas.vue'
import type { PanoramaEdge, PanoramaGraph } from '@/types/panorama'
import { dataTypeLabel } from '@/types/panorama'

const router = useRouter()
const loading = ref(false)
const graph = ref<PanoramaGraph | null>(null)
const includeEndpointLinks = ref(true)
const selectedAssetId = ref<number | null>(null)
const selectedEdge = ref<PanoramaEdge | null>(null)
const canvasRef = ref<InstanceType<typeof PanoramaGraphCanvas> | null>(null)

async function load() {
  loading.value = true
  selectedAssetId.value = null
  selectedEdge.value = null
  try {
    graph.value = await getPanoramaGraph(includeEndpointLinks.value)
  } catch (e) {
    graph.value = null
    ElMessage.error(e instanceof Error ? e.message : '加载全景图失败')
  } finally {
    loading.value = false
  }
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
  canvasRef.value?.zoomToFit()
}

function zoomIn() {
  canvasRef.value?.zoomIn()
}

function zoomOut() {
  canvasRef.value?.zoomOut()
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <AppNav />

    <header class="topbar">
      <div>
        <el-button link @click="router.push('/')">← 返回资产列表</el-button>
        <h1>资产全景图</h1>
        <p class="meta">
          血缘视角：派生关系与跨资产落点衔接；点击资产卡片可下钻单资产成图
        </p>
      </div>
      <div class="actions">
        <el-button link type="primary" @click="openGuide">流向配置说明</el-button>
        <el-button @click="zoomOut">缩小</el-button>
        <el-button @click="zoomIn">放大</el-button>
        <el-button @click="zoomToFit">适配</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <div class="toolbar card">
      <el-switch v-model="includeEndpointLinks" active-text="含落点衔接" @change="load" />
      <span class="tip" v-if="graph">
        共 {{ graph.assetCount }} 个资产 · {{ graph.edgeCount }} 条跨资产关系
      </span>
      <span class="tip muted">无连线的资产显示在下方（孤立节点）</span>
    </div>

    <div class="workspace">
      <PanoramaGraphCanvas
        ref="canvasRef"
        class="canvas-panel"
        :graph="graph"
        :selected-asset-id="selectedAssetId"
        @select-asset="selectedAssetId = $event; selectedEdge = null"
        @select-edge="selectedEdge = $event; selectedAssetId = null"
      />

      <aside class="side card">
        <template v-if="selectedAssetId && graph">
          <h2>资产</h2>
          <template v-for="n in graph.nodes" :key="n.assetId">
            <dl v-if="n.assetId === selectedAssetId" class="detail">
              <div><dt>名称</dt><dd>{{ n.name }}</dd></div>
              <div><dt>编码</dt><dd>{{ n.code }}</dd></div>
              <div><dt>类型</dt><dd>{{ dataTypeLabel(n.dataType) }}</dd></div>
              <div><dt>状态</dt><dd>{{ n.status }}</dd></div>
              <div><dt>主流向</dt><dd>{{ n.primaryFlowCount }} 条</dd></div>
              <div v-if="n.derivationInCount">
                <dt>作为派生输入</dt><dd>{{ n.derivationInCount }} 次</dd>
              </div>
              <div v-if="n.derivationOutCount">
                <dt>派生输出</dt><dd>{{ n.derivationOutCount }} 条定义</dd>
              </div>
            </dl>
          </template>
          <div class="side-actions">
            <el-button type="primary" @click="openAssetGraph(selectedAssetId)">一键成图</el-button>
            <el-button @click="openAssetFlows(selectedAssetId)">管理流向</el-button>
          </div>
        </template>

        <template v-else-if="selectedEdge">
          <h2>跨资产关系</h2>
          <dl class="detail">
            <div>
              <dt>类型</dt>
              <dd>{{ selectedEdge.type === 'DERIVE' ? '派生输入' : '共享落点衔接' }}</dd>
            </div>
            <div><dt>说明</dt><dd>{{ selectedEdge.label }}</dd></div>
            <div v-if="selectedEdge.endpointLabel">
              <dt>共享落点</dt><dd>{{ selectedEdge.endpointLabel }}</dd>
            </div>
            <div>
              <dt>上游资产 ID</dt><dd>{{ selectedEdge.sourceAssetId }}</dd>
            </div>
            <div>
              <dt>下游资产 ID</dt><dd>{{ selectedEdge.targetAssetId }}</dd>
            </div>
          </dl>
          <div class="side-actions">
            <el-button @click="openAssetGraph(selectedEdge.sourceAssetId)">上游成图</el-button>
            <el-button type="primary" @click="openAssetGraph(selectedEdge.targetAssetId)">
              下游成图
            </el-button>
          </div>
        </template>

        <template v-else>
          <h2>说明</h2>
          <ul class="help">
            <li><strong>派生</strong>：多输入资产加工为输出资产（如实测+遥信→拼接数据 D）</li>
            <li><strong>落点衔接</strong>：上游资产某主流向的目标落点 = 下游资产某主流向的源落点</li>
            <li>点击资产卡片查看详情并下钻；点击连线查看跨资产关系</li>
          </ul>
        </template>
      </aside>
    </div>
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
  color: #64748b;
  font-size: 13px;
  max-width: 560px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.card {
  background: #fff;
  border: 1px solid #d5e0db;
  border-radius: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.tip {
  font-size: 13px;
  color: #334155;
}

.tip.muted {
  color: #94a3b8;
  font-size: 12px;
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
  color: #94a3b8;
}

.detail dd {
  margin: 2px 0 0;
  font-size: 13px;
  color: #334155;
  word-break: break-all;
}

.side-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.help {
  margin: 0;
  padding-left: 1.2em;
  font-size: 13px;
  color: #64748b;
  line-height: 1.7;
}

.help strong {
  color: #334155;
}

@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}
</style>
