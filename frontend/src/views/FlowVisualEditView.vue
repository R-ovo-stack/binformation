<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAsset } from '@/api/asset'
import { createFlow, deleteFlow, getFlow, listFlowsByAsset, updateFlow } from '@/api/flow'
import { listEndpointOptions, listExecutorOptions } from '@/api/reference'
import FlowBoardCanvas, { type BoardFlowEdge } from '@/components/FlowBoardCanvas.vue'
import EndpointTreeSelect from '@/components/EndpointTreeSelect.vue'
import EndpointQuickEditDrawer from '@/components/EndpointQuickEditDrawer.vue'
import type { DataAsset } from '@/types/graph'
import {
  FLOW_METHOD_OPTIONS,
  FLOW_PURPOSE_OPTIONS,
  FLOW_STATUS_OPTIONS,
  emptyFlow,
  emptyPath,
  emptyStep,
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
const flowDetailsCache = ref<Record<number, FlowDetail>>({})
const executors = ref<ExecutorOption[]>([])
const hosts = computed(() => allEndpoints.value.filter((e) => e.type === 'HOST'))

const selectedEdgeId = ref<string | null>(null)
const selectedPathIndex = ref(0)
const draft = ref<FlowDetail | null>(null)
const editing = ref<FlowDetail | null>(null)
const addEndpointId = ref<number | null>(null)
const canvasRef = ref<InstanceType<typeof FlowBoardCanvas> | null>(null)
const boardKey = ref(0)
const endpointEditOpen = ref(false)
const endpointEditId = ref<number | null>(null)

const assetId = computed(() => Number(props.id))

const flowDetailsForBoard = computed(() => {
  const map = { ...flowDetailsCache.value }
  if (editing.value?.id) map[editing.value.id] = editing.value
  return map
})

const boardEdges = computed<BoardFlowEdge[]>(() => {
  const edges: BoardFlowEdge[] = flowSummaries.value.map((f) => ({
    id: `flow-${f.id}`,
    flowId: f.id,
    sourceEndpointId: f.sourceEndpointId,
    targetEndpointId: f.targetEndpointId,
    purpose: f.purpose,
    primary: f.primary,
    pathCount: f.pathCount,
  }))
  if (draft.value?.sourceEndpointId && draft.value.targetEndpointId) {
    edges.push({
      id: 'draft',
      flowId: null,
      sourceEndpointId: draft.value.sourceEndpointId,
      targetEndpointId: draft.value.targetEndpointId,
      purpose: draft.value.purpose,
      primary: draft.value.primary,
      pathCount: draft.value.paths.length,
      draft: true,
    })
  }
  return edges
})

const panelFlow = computed(() => (selectedEdgeId.value === 'draft' ? draft.value : editing.value))
const isDraft = computed(() => selectedEdgeId.value === 'draft')
const activePath = computed(() => {
  const flow = panelFlow.value
  if (!flow?.paths.length) return null
  const idx = Math.min(Math.max(selectedPathIndex.value, 0), flow.paths.length - 1)
  return flow.paths[idx] ?? null
})
const panelTitle = computed(() => {
  if (!panelFlow.value) return '流向编辑'
  if (isDraft.value) return '新建流向（未保存）'
  return `编辑流向 #${panelFlow.value.id}`
})

const pathTabOptions = computed(() =>
  (panelFlow.value?.paths || []).map((p, i) => ({
    name: String(i),
    label: p.name?.trim() || `路径 ${i + 1}`,
  })),
)

function endpointLabel(ep: EndpointOption) {
  return `${ep.breadcrumb} / ${ep.name}`
}

function endpointShortName(id: number | null | undefined): string {
  if (id == null) return '—'
  const ep = allEndpoints.value.find((e) => e.id === id)
  return ep?.name ?? `#${id}`
}

function flowPairLabel(flow: FlowSummary): string {
  return `${endpointShortName(flow.sourceEndpointId)} → ${endpointShortName(flow.targetEndpointId)}`
}

function buildNewFlowNotice(sourceEndpointId: number, targetEndpointId: number): string {
  const sourceName = endpointShortName(sourceEndpointId)
  const targetName = endpointShortName(targetEndpointId)
  const lines = [
    `将新建一条独立流向：${sourceName} → ${targetName}。`,
    '保存后不会修改其它已有流向；每条连线对应一条 Flow。',
  ]

  const inbound = flowSummaries.value.filter((f) => f.targetEndpointId === sourceEndpointId)
  const outbound = flowSummaries.value.filter((f) => f.sourceEndpointId === targetEndpointId)

  if (inbound.length) {
    lines.push(
      `落点「${sourceName}」已是 ${inbound.map(flowPairLabel).join('、')} 的目标；保存后将与之衔接，形成多段链路。`,
    )
  }
  if (outbound.length) {
    lines.push(
      `落点「${targetName}」已是 ${outbound.map(flowPairLabel).join('、')} 的源；保存后将与之衔接，形成多段链路。`,
    )
  }

  return lines.join(' ')
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
    const flowDetails = await Promise.all(flows.map((f) => getFlow(f.id)))
    const nextDetailsCache = Object.fromEntries(
      flowDetails.filter((d) => d.id != null).map((d) => [d.id!, normalizeDetail(d)]),
    )

    const used = new Set<number>()
    flows.forEach((f) => {
      used.add(f.sourceEndpointId)
      used.add(f.targetEndpointId)
    })

    let nextEditing: FlowDetail | null = null
    let nextSelected: string | null = null
    if (props.flowId) {
      const detail = await getFlow(Number(props.flowId))
      used.add(detail.sourceEndpointId!)
      used.add(detail.targetEndpointId!)
      nextEditing = normalizeDetail(detail)
      nextSelected = `flow-${detail.id}`
    } else if (
      selectedEdgeId.value &&
      selectedEdgeId.value !== 'draft' &&
      flows.some((f) => `flow-${f.id}` === selectedEdgeId.value)
    ) {
      nextSelected = selectedEdgeId.value
      try {
        nextEditing = normalizeDetail(await getFlow(Number(selectedEdgeId.value.replace(/^flow-/, ''))))
      } catch {
        nextSelected = null
        nextEditing = null
      }
    }

    const nextCanvasIds = used.size
      ? [...used]
      : eps.slice(0, Math.min(12, eps.length)).map((e) => e.id)

    // 一次性提交状态，避免多次 watch 叠图画布
    draft.value = null
    asset.value = assetData
    executors.value = execs
    allEndpoints.value = eps
    flowSummaries.value = flows
    flowDetailsCache.value = nextDetailsCache
    canvasEndpointIds.value = nextCanvasIds
    editing.value = nextEditing
    selectedEdgeId.value = nextSelected
    selectedPathIndex.value = 0
    boardKey.value += 1
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
  flow.paths.forEach((p, i) => {
    if (!p.steps.length) p.steps.push(emptyStep(1))
    if (p.sortOrder == null) p.sortOrder = i
  })
  if (selectedPathIndex.value >= flow.paths.length) {
    selectedPathIndex.value = Math.max(0, flow.paths.length - 1)
  }
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
    selectedPathIndex.value = 0
    return
  }
  const next = emptyFlow(assetId.value)
  next.sourceEndpointId = sourceEndpointId
  next.targetEndpointId = targetEndpointId
  ensureWorkingPath(next)
  draft.value = next
  editing.value = null
  selectedEdgeId.value = 'draft'
  selectedPathIndex.value = 0
  ensureEndpointsOnCanvas(sourceEndpointId, targetEndpointId)
  ElMessage({
    message: buildNewFlowNotice(sourceEndpointId, targetEndpointId),
    type: 'warning',
    duration: 9000,
    showClose: true,
  })
}

async function selectEdge(edgeId: string | null) {
  selectedEdgeId.value = edgeId
  selectedPathIndex.value = 0
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

function onEditEndpoint(endpointId: number) {
  endpointEditId.value = endpointId
  endpointEditOpen.value = true
}

async function onEndpointSaved(endpointId: number) {
  try {
    const [eps, execs] = await Promise.all([listEndpointOptions(), listExecutorOptions()])
    allEndpoints.value = eps
    executors.value = execs
    ensureEndpointsOnCanvas(endpointId)
    boardKey.value += 1
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '刷新落点失败')
  }
}

function addEndpointToCanvas() {
  if (!addEndpointId.value) {
    ElMessage.warning('请先选择落点')
    return
  }
  ensureEndpointsOnCanvas(addEndpointId.value)
  addEndpointId.value = null
  ElMessage.success('已加入画布（仅展示落点，不会自动创建流向；需从源落点拖线到目标落点）')
}

function addPath() {
  const flow = panelFlow.value
  if (!flow) return
  ensureWorkingPath(flow)
  const next = emptyPath(flow.paths.length)
  next.name = `路径 ${flow.paths.length + 1}`
  flow.paths.push(next)
  selectedPathIndex.value = flow.paths.length - 1
}

function removePath() {
  const flow = panelFlow.value
  if (!flow || flow.paths.length <= 1) {
    ElMessage.warning('至少保留一条路径')
    return
  }
  flow.paths.splice(selectedPathIndex.value, 1)
  flow.paths.forEach((p, i) => {
    p.sortOrder = i
  })
  selectedPathIndex.value = Math.min(selectedPathIndex.value, flow.paths.length - 1)
}

function onPathTabChange(name: string | number) {
  selectedPathIndex.value = Number(name)
}

function addStep() {
  const path = activePath.value
  if (!path) return
  const nextSeq = path.steps.length ? Math.max(...path.steps.map((s) => s.seq)) + 1 : 1
  path.steps.push(emptyStep(nextSeq))
}

function removeStep(index: number) {
  const path = activePath.value
  if (!path) return
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
  const path = activePath.value
  if (!path) return
  const step = path.steps[stepIndex]
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
  for (const [pi, path] of flow.paths.entries()) {
    if (!path.name.trim()) path.name = `路径 ${pi + 1}`
    path.sortOrder = path.sortOrder ?? pi
    for (const step of path.steps) {
      if (!step.executorId) {
        ElMessage.warning(`路径「${path.name}」存在未选择程序/脚本的步骤`)
        selectedPathIndex.value = pi
        return
      }
    }
  }

  const payloadBase = toSavePayload(flow)

  saving.value = true
  try {
    if (isDraft.value) {
      const created = await createFlow(assetId.value, payloadBase)
      ElMessage.success('已创建新流向（与已有流向独立保存）')
      draft.value = null
      await refreshFlows()
      selectedEdgeId.value = `flow-${created.id}`
      editing.value = normalizeDetail(created)
      if (created.id) flowDetailsCache.value[created.id] = editing.value
      selectedPathIndex.value = Math.min(selectedPathIndex.value, editing.value.paths.length - 1)
    } else if (flow.id) {
      const updated = await updateFlow(flow.id, payloadBase)
      ElMessage.success('已保存')
      await refreshFlows()
      editing.value = normalizeDetail(updated)
      if (updated.id) flowDetailsCache.value[updated.id] = editing.value
      selectedPathIndex.value = Math.min(selectedPathIndex.value, editing.value.paths.length - 1)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function refreshFlows() {
  const flows = await listFlowsByAsset(assetId.value)
  flowSummaries.value = flows
  const details = await Promise.all(flows.map((f) => getFlow(f.id)))
  flowDetailsCache.value = Object.fromEntries(
    details.filter((d) => d.id != null).map((d) => [d.id!, normalizeDetail(d)]),
  )
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

const draftNotice = computed(() => {
  if (!isDraft.value || !draft.value?.sourceEndpointId || !draft.value.targetEndpointId) {
    return ''
  }
  return buildNewFlowNotice(draft.value.sourceEndpointId, draft.value.targetEndpointId)
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
        <el-button link type="primary" @click="openGuide">配置说明</el-button>
        <el-button @click="openFormEditor">表单编辑</el-button>
        <el-button @click="zoomOut">缩小</el-button>
        <el-button @click="zoomIn">放大</el-button>
        <el-button @click="zoomToFit">适配</el-button>
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
      <span class="toolbar-tip">
        双击落点可改属性；从落点<strong>右侧</strong>拖线到另一落点<strong>左侧</strong>可新建流向
      </span>
    </div>

    <div class="workspace">
      <FlowBoardCanvas
        :key="boardKey"
        ref="canvasRef"
        class="board"
        :asset="asset"
        :canvas-endpoint-ids="canvasEndpointIds"
        :all-endpoints="allEndpoints"
        :executors="executors"
        :flow-details-by-id="flowDetailsForBoard"
        :draft="draft"
        :edges="boardEdges"
        :selected-edge-id="selectedEdgeId"
        @select-edge="selectEdge"
        @connect="onConnect"
        @edit-endpoint="onEditEndpoint"
      />

      <aside class="side card">
        <template v-if="panelFlow">
          <div class="side-head">
            <h2>{{ panelTitle }}</h2>
            <el-button v-if="isDraft" link @click="discardDraft">丢弃</el-button>
          </div>

          <el-alert
            v-if="isDraft && draftNotice"
            class="draft-alert"
            type="warning"
            :closable="false"
            show-icon
            title="新建流向"
            :description="draftNotice"
          />

          <el-alert
            v-else-if="!isDraft && panelFlow?.id"
            class="draft-alert"
            type="info"
            :closable="false"
            show-icon
            title="编辑已有流向"
            description="修改源/目标或路径/步骤后保存，会更新当前这条流向。若需另一条源→目标，请在画布拖新连线。"
          />

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

          <div class="paths-head">
            <h3>路径</h3>
            <div class="paths-actions">
              <el-button size="small" @click="addPath">添加路径</el-button>
              <el-button
                v-if="(panelFlow.paths?.length || 0) > 1"
                size="small"
                type="danger"
                plain
                @click="removePath"
              >
                删除当前
              </el-button>
            </div>
          </div>
          <p class="steps-tip">
            同一源→目标可有多条备选路径（如主备通道）；每条路径内步骤按顺序执行。
          </p>

          <el-tabs
            :model-value="String(selectedPathIndex)"
            type="card"
            class="path-tabs"
            @tab-change="onPathTabChange"
          >
            <el-tab-pane
              v-for="tab in pathTabOptions"
              :key="tab.name"
              :label="tab.label"
              :name="tab.name"
            />
          </el-tabs>

          <template v-if="activePath">
            <el-form label-width="72px" class="side-form path-meta" size="small">
              <el-form-item label="名称">
                <el-input v-model="activePath.name" placeholder="如 cloud201→idc306" />
              </el-form-item>
              <el-form-item label="排序">
                <el-input-number v-model="activePath.sortOrder" :min="0" controls-position="right" />
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="activePath.enabled" />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="activePath.remark" placeholder="可选" />
              </el-form-item>
            </el-form>

            <div class="steps-head">
              <h3>步骤</h3>
              <el-button size="small" @click="addStep">添加步骤</el-button>
            </div>

            <div v-for="(step, index) in activePath.steps" :key="index" class="step">
              <div class="step-title">
                <strong>步骤 {{ step.seq }}</strong>
                <el-button
                  v-if="activePath.steps.length > 1"
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
                  <el-select
                    v-model="step.hostId"
                    filterable
                    clearable
                    placeholder="可选"
                    style="width: 100%"
                  >
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
          </template>

          <div class="side-actions">
            <el-button type="primary" :loading="saving" @click="savePanel">
              {{ isDraft ? '创建新流向' : '保存修改' }}
            </el-button>
            <el-button v-if="!isDraft" type="danger" plain @click="removePanelFlow">删除流向</el-button>
            <el-button link type="primary" @click="openFormEditor">打开表单编辑</el-button>
          </div>
        </template>
        <template v-else>
          <h2>流向编辑</h2>
          <p class="empty-hint">
            在画布上从落点右侧拖线到另一落点左侧，可<strong>新建</strong>一条流向；点击已有连线可<strong>编辑</strong>该流向。
            <strong>双击落点</strong>可修改其属性。编辑 A→B 时再拖 B→C，会新建独立的 B→C 流向。
          </p>
        </template>
      </aside>
    </div>

    <EndpointQuickEditDrawer
      v-model="endpointEditOpen"
      :endpoint-id="endpointEditId"
      @saved="onEndpointSaved"
    />
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
  color: var(--muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
  gap: 10px;
  padding: 10px 12px;
  flex-wrap: wrap;
}

.toolbar-label {
  font-size: 13px;
  color: var(--ink-soft);
  white-space: nowrap;
}

.add-select {
  width: min(420px, 100%);
}

.toolbar-tip {
  font-size: 12px;
  color: var(--muted-soft);
}

.workspace {
  flex: 1;
  min-height: 560px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
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

.draft-alert {
  margin-bottom: 12px;
}

.draft-alert :deep(.el-alert__description) {
  line-height: 1.55;
  font-size: 13px;
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
  color: var(--muted-soft);
}

.pair dd {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--ink-soft);
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

.steps-head h3,
.paths-head h3 {
  margin: 0;
  font-size: 14px;
}

.paths-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0 4px;
  gap: 8px;
}

.paths-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.path-tabs {
  margin-bottom: 8px;
}

.path-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.path-tabs :deep(.el-tabs__nav-wrap) {
  overflow-x: auto;
}

.path-meta {
  margin-bottom: 4px;
}

.steps-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--muted-soft);
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
  color: var(--muted);
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
