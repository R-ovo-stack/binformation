<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAsset } from '@/api/asset'
import { createFlow, deleteFlow, getFlow, listFlowsByAsset, updateFlow } from '@/api/flow'
import { listEndpointOptions, listExecutorOptions } from '@/api/reference'
import FlowBoardCanvas, { type BoardFlowEdge } from '@/components/FlowBoardCanvas.vue'
import EndpointTreeSelect from '@/components/EndpointTreeSelect.vue'
import type { DataAsset } from '@/types/graph'
import {
  FLOW_METHOD_OPTIONS,
  FLOW_PURPOSE_OPTIONS,
  FLOW_STATUS_OPTIONS,
  emptyFlow,
  emptyPath,
  emptyStep,
  purposeLabel,
  toSavePayload,
  type EndpointOption,
  type ExecutorOption,
  type FlowDetail,
  type FlowSummary,
} from '@/types/flow'

const props = defineProps<{ id: string; flowId?: string }>()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const asset = ref<DataAsset | null>(null)
const allEndpoints = ref<EndpointOption[]>([])
const canvasEndpointIds = ref<number[]>([])
const flowSummaries = ref<FlowSummary[]>([])
const executors = ref<ExecutorOption[]>([])
const hosts = computed(() => allEndpoints.value.filter((e) => e.type === 'HOST'))

const selectedEdgeId = ref<string | null>(null)
const draft = ref<FlowDetail | null>(null)
const editing = ref<FlowDetail | null>(null)
const addEndpointId = ref<number | null>(null)
const canvasRef = ref<InstanceType<typeof FlowBoardCanvas> | null>(null)

const assetId = computed(() => Number(props.id))

const canvasEndpoints = computed(() =>
  allEndpoints.value.filter((ep) => canvasEndpointIds.value.includes(ep.id)),
)

const boardEdges = computed<BoardFlowEdge[]>(() => {
  const edges: BoardFlowEdge[] = flowSummaries.value.map((f) => ({
    id: `flow-${f.id}`,
    flowId: f.id,
    sourceEndpointId: f.sourceEndpointId,
    targetEndpointId: f.targetEndpointId,
    purpose: f.purpose,
    primary: f.primary,
  }))
  if (draft.value?.sourceEndpointId && draft.value.targetEndpointId) {
    edges.push({
      id: 'draft',
      flowId: null,
      sourceEndpointId: draft.value.sourceEndpointId,
      targetEndpointId: draft.value.targetEndpointId,
      purpose: draft.value.purpose,
      primary: draft.value.primary,
      draft: true,
    })
  }
  return edges
})

const panelFlow = computed(() => (selectedEdgeId.value === 'draft' ? draft.value : editing.value))
const isDraft = computed(() => selectedEdgeId.value === 'draft')
const panelTitle = computed(() => {
  if (!panelFlow.value) return '流向编辑'
  if (isDraft.value) return '新建流向（未保存）'
  return `编辑流向 #${panelFlow.value.id}`
})

function endpointLabel(ep: EndpointOption) {
  return `${ep.breadcrumb} / ${ep.name}`
}

function ensureEndpointsOnCanvas(...ids: number[]) {
  const set = new Set(canvasEndpointIds.value)
  ids.forEach((id) => {
    if (Number.isFinite(id)) set.add(id)
  })
  canvasEndpointIds.value = [...set]
}

async function load() {
  if (!Number.isFinite(assetId.value)) {
    ElMessage.error('无效的资产 ID')
    return
  }
  loading.value = true
  try {
    const [assetData, flows, eps, execs] = await Promise.all([
      getAsset(assetId.value),
      listFlowsByAsset(assetId.value),
      listEndpointOptions(),
      listExecutorOptions(),
    ])
    asset.value = assetData
    flowSummaries.value = flows
    allEndpoints.value = eps
    executors.value = execs

    const used = new Set<number>()
    flows.forEach((f) => {
      used.add(f.sourceEndpointId)
      used.add(f.targetEndpointId)
    })
    if (props.flowId) {
      const detail = await getFlow(Number(props.flowId))
      used.add(detail.sourceEndpointId!)
      used.add(detail.targetEndpointId!)
      editing.value = normalizeDetail(detail)
      selectedEdgeId.value = `flow-${detail.id}`
    }
    canvasEndpointIds.value = [...used]
    if (!canvasEndpointIds.value.length && eps.length) {
      // 空资产时放入各区少量代表落点，便于开始拖线
      canvasEndpointIds.value = eps.slice(0, Math.min(12, eps.length)).map((e) => e.id)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function normalizeDetail(detail: FlowDetail): FlowDetail {
  return {
    ...detail,
    paths: detail.paths.length
      ? detail.paths.map((p) => ({
          ...p,
          steps: p.steps.length ? p.steps : [emptyStep(1)],
        }))
      : [emptyPath(0)],
  }
}

function ensureWorkingPath(flow: FlowDetail) {
  if (!flow.paths.length) flow.paths.push(emptyPath(0))
  if (!flow.paths[0].steps.length) flow.paths[0].steps.push(emptyStep(1))
}

function onConnect(sourceEndpointId: number, targetEndpointId: number) {
  const existing = flowSummaries.value.find(
    (f) => f.sourceEndpointId === sourceEndpointId && f.targetEndpointId === targetEndpointId,
  )
  if (existing) {
    void selectEdge(`flow-${existing.id}`)
    ElMessage.info('该源→目标已有流向，已打开编辑')
    return
  }
  if (
    draft.value?.sourceEndpointId === sourceEndpointId &&
    draft.value?.targetEndpointId === targetEndpointId
  ) {
    selectedEdgeId.value = 'draft'
    return
  }
  const next = emptyFlow(assetId.value)
  next.sourceEndpointId = sourceEndpointId
  next.targetEndpointId = targetEndpointId
  ensureWorkingPath(next)
  draft.value = next
  editing.value = null
  selectedEdgeId.value = 'draft'
  ensureEndpointsOnCanvas(sourceEndpointId, targetEndpointId)
}

async function selectEdge(edgeId: string | null) {
  selectedEdgeId.value = edgeId
  if (!edgeId) {
    editing.value = null
    return
  }
  if (edgeId === 'draft') {
    editing.value = null
    return
  }
  const flowId = Number(edgeId.replace(/^flow-/, ''))
  if (!Number.isFinite(flowId)) return
  loading.value = true
  try {
    const detail = await getFlow(flowId)
    editing.value = normalizeDetail(detail)
    ensureWorkingPath(editing.value)
    ensureEndpointsOnCanvas(detail.sourceEndpointId!, detail.targetEndpointId!)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载流向失败')
    selectedEdgeId.value = null
  } finally {
    loading.value = false
  }
}

function addEndpointToCanvas() {
  if (!addEndpointId.value) {
    ElMessage.warning('请先选择落点')
    return
  }
  ensureEndpointsOnCanvas(addEndpointId.value)
  addEndpointId.value = null
  ElMessage.success('已加入画布')
}

function addStep() {
  const flow = panelFlow.value
  if (!flow) return
  ensureWorkingPath(flow)
  const path = flow.paths[0]
  const nextSeq = path.steps.length ? Math.max(...path.steps.map((s) => s.seq)) + 1 : 1
  path.steps.push(emptyStep(nextSeq))
}

function removeStep(index: number) {
  const flow = panelFlow.value
  if (!flow) return
  const path = flow.paths[0]
  if (path.steps.length <= 1) {
    ElMessage.warning('至少保留一个步骤')
    return
  }
  path.steps.splice(index, 1)
  path.steps.forEach((s, i) => {
    s.seq = i + 1
  })
}

function onExecutorChange(stepIndex: number, executorId: number | null) {
  const flow = panelFlow.value
  if (!flow) return
  const step = flow.paths[0].steps[stepIndex]
  step.executorId = executorId
  if (!step.hostId && executorId) {
    const ex = executors.value.find((e) => e.id === executorId)
    if (ex?.defaultHostId) step.hostId = ex.defaultHostId
  }
}

async function savePanel() {
  const flow = panelFlow.value
  if (!flow?.sourceEndpointId || !flow.targetEndpointId) {
    ElMessage.warning('请先拖线选择源与目标落点')
    return
  }
  ensureWorkingPath(flow)
  const path = flow.paths[0]
  if (!path.name.trim()) path.name = '默认路径'
  for (const step of path.steps) {
    if (!step.executorId) {
      ElMessage.warning('请为每个步骤选择程序/脚本')
      return
    }
  }
  // 可视化模式只保存当前编辑的第一条路径；已有多路径时保留其余路径
  const payloadBase = toSavePayload(flow)
  if (!isDraft.value && editing.value && editing.value.paths.length > 1) {
    payloadBase.paths = [
      payloadBase.paths[0],
      ...editing.value.paths.slice(1).map((p, index) => ({
        name: p.name,
        enabled: p.enabled,
        sortOrder: p.sortOrder ?? index + 1,
        remark: p.remark,
        steps: p.steps.map((s, i) => ({
          seq: s.seq ?? i + 1,
          hostId: s.hostId,
          executorId: s.executorId!,
          method: s.method,
          remark: s.remark,
        })),
      })),
    ]
  }

  saving.value = true
  try {
    if (isDraft.value) {
      const created = await createFlow(assetId.value, payloadBase)
      ElMessage.success('已创建流向')
      draft.value = null
      await refreshFlows()
      selectedEdgeId.value = `flow-${created.id}`
      editing.value = normalizeDetail(created)
    } else if (flow.id) {
      const updated = await updateFlow(flow.id, payloadBase)
      ElMessage.success('已保存')
      await refreshFlows()
      editing.value = normalizeDetail(updated)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function refreshFlows() {
  flowSummaries.value = await listFlowsByAsset(assetId.value)
}

async function removePanelFlow() {
  const flow = panelFlow.value
  if (!flow) return
  if (isDraft.value) {
    draft.value = null
    selectedEdgeId.value = null
    return
  }
  if (!flow.id) return
  try {
    await ElMessageBox.confirm(`确定删除流向 #${flow.id} 吗？`, '删除确认', { type: 'warning' })
    await deleteFlow(flow.id)
    ElMessage.success('已删除')
    editing.value = null
    selectedEdgeId.value = null
    await refreshFlows()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

function discardDraft() {
  draft.value = null
  if (selectedEdgeId.value === 'draft') selectedEdgeId.value = null
}

function openFormEditor() {
  if (panelFlow.value?.id) {
    void router.push({
      name: 'flow-edit',
      params: { id: props.id, flowId: String(panelFlow.value.id) },
    })
  } else {
    void router.push({ name: 'flow-create', params: { id: props.id } })
  }
}

function back() {
  void router.push({ name: 'asset-flows', params: { id: props.id } })
}

const sourceLabel = computed(() => {
  const id = panelFlow.value?.sourceEndpointId
  const ep = allEndpoints.value.find((e) => e.id === id)
  return ep ? endpointLabel(ep) : id ? `#${id}` : '—'
})

const targetLabel = computed(() => {
  const id = panelFlow.value?.targetEndpointId
  const ep = allEndpoints.value.find((e) => e.id === id)
  return ep ? endpointLabel(ep) : id ? `#${id}` : '—'
})

onMounted(load)
watch(
  () => [props.id, props.flowId],
  () => {
    draft.value = null
    void load()
  },
)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="back">← 返回流向列表</el-button>
        <h1>可视化编辑流向</h1>
        <p class="meta" v-if="asset">
          {{ asset.name }} · {{ asset.code }} · 可选模式（表单编辑仍可用）
        </p>
      </div>
      <div class="actions">
        <el-button @click="openFormEditor">表单编辑</el-button>
        <el-button @click="canvasRef?.zoomToFit()">适配画布</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <div class="toolbar card">
      <span class="toolbar-label">添加落点到画布</span>
      <EndpointTreeSelect
        v-model="addEndpointId"
        class="add-select"
        :options="allEndpoints"
        clearable
        placeholder="搜索落点…"
      />
      <el-button type="primary" plain @click="addEndpointToCanvas">加入</el-button>
      <span class="toolbar-tip">已有流向的端点会自动出现；可继续补充其它落点后拖线</span>
    </div>

    <div class="workspace">
      <FlowBoardCanvas
        ref="canvasRef"
        class="board"
        :endpoints="canvasEndpoints"
        :edges="boardEdges"
        :selected-edge-id="selectedEdgeId"
        @select-edge="selectEdge"
        @connect="onConnect"
      />

      <aside class="side card">
        <template v-if="panelFlow">
          <div class="side-head">
            <h2>{{ panelTitle }}</h2>
            <el-button v-if="isDraft" link @click="discardDraft">丢弃</el-button>
          </div>

          <dl class="pair">
            <div>
              <dt>源</dt>
              <dd>{{ sourceLabel }}</dd>
            </div>
            <div>
              <dt>目标</dt>
              <dd>{{ targetLabel }}</dd>
            </div>
          </dl>

          <el-form label-width="72px" class="side-form">
            <el-form-item label="用途">
              <el-select v-model="panelFlow.purpose" style="width: 100%">
                <el-option
                  v-for="o in FLOW_PURPOSE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="panelFlow.status" style="width: 100%">
                <el-option
                  v-for="o in FLOW_STATUS_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="主流向">
              <el-switch v-model="panelFlow.primary" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="panelFlow.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>

          <div class="steps-head">
            <h3>路径步骤</h3>
            <el-button size="small" @click="addStep">添加步骤</el-button>
          </div>
          <p class="steps-tip">
            可视化模式编辑默认路径；多路径请用「表单编辑」。当前用途：{{
              purposeLabel(panelFlow.purpose)
            }}
          </p>

          <div
            v-for="(step, index) in panelFlow.paths[0]?.steps || []"
            :key="index"
            class="step"
          >
            <div class="step-title">
              <strong>步骤 {{ step.seq }}</strong>
              <el-button
                v-if="(panelFlow.paths[0]?.steps.length || 0) > 1"
                link
                type="danger"
                @click="removeStep(index)"
              >
                删除
              </el-button>
            </div>
            <el-form label-width="72px" size="small">
              <el-form-item label="程序">
                <el-select
                  :model-value="step.executorId"
                  filterable
                  placeholder="选择程序/脚本"
                  style="width: 100%"
                  @update:model-value="onExecutorChange(index, $event)"
                >
                  <el-option
                    v-for="ex in executors"
                    :key="ex.id"
                    :label="`${ex.name} (${ex.code})`"
                    :value="ex.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="主机">
                <el-select v-model="step.hostId" filterable clearable placeholder="可选" style="width: 100%">
                  <el-option
                    v-for="h in hosts"
                    :key="h.id"
                    :label="endpointLabel(h)"
                    :value="h.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="方法">
                <el-select v-model="step.method" style="width: 100%">
                  <el-option
                    v-for="o in FLOW_METHOD_OPTIONS"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="step.remark" />
              </el-form-item>
            </el-form>
          </div>

          <div class="side-actions">
            <el-button type="primary" :loading="saving" @click="savePanel">保存</el-button>
            <el-button v-if="!isDraft" type="danger" plain @click="removePanelFlow">删除流向</el-button>
            <el-button link type="primary" @click="openFormEditor">打开表单编辑</el-button>
          </div>
        </template>
        <template v-else>
          <h2>流向编辑</h2>
          <p class="empty-hint">
            在画布上拖线创建流向，或点击已有连线进行编辑。步骤在右侧配置后保存即可写入台账。
          </p>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.page {
  height: 100%;
  min-height: 100vh;
  padding: 16px 20px 24px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
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

.card {
  background: #fff;
  border: 1px solid #d5e0db;
  border-radius: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  flex-wrap: wrap;
}

.toolbar-label {
  font-size: 13px;
  color: #334155;
  white-space: nowrap;
}

.add-select {
  width: min(420px, 100%);
}

.toolbar-tip {
  font-size: 12px;
  color: #94a3b8;
}

.workspace {
  flex: 1;
  min-height: 560px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 12px;
}

.board {
  min-height: 560px;
}

.side {
  padding: 14px;
  overflow: auto;
  min-height: 560px;
}

.side-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.side h2 {
  margin: 0;
  font-size: 16px;
}

.pair {
  margin: 0 0 12px;
  display: grid;
  gap: 8px;
}

.pair dt {
  font-size: 12px;
  color: #94a3b8;
}

.pair dd {
  margin: 2px 0 0;
  font-size: 12px;
  color: #334155;
  line-height: 1.4;
  word-break: break-all;
}

.side-form {
  margin-bottom: 8px;
}

.steps-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0 4px;
}

.steps-head h3 {
  margin: 0;
  font-size: 14px;
}

.steps-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: #94a3b8;
}

.step {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 10px 2px;
  margin-bottom: 10px;
}

.step-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.side-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.empty-hint {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .side {
    min-height: 320px;
  }
}
</style>
