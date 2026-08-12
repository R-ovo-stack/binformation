<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Graph } from '@antv/x6'
import type { EndpointOption, ExecutorOption, FlowDetail } from '@/types/flow'
import type { AssetGraph, GraphEdge } from '@/types/graph'
import { purposeLabel } from '@/types/flow'
import {
  boardFlowIdFromCell,
  buildBoardAssetGraph,
  isBoardEndpointNode,
  type BoardFlowEdge,
} from '@/utils/flowBoardGraph'
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  edgeStroke,
  endpointTypeLabel,
  expandDisplayEdges,
  layoutGraph,
  visibleRelations,
} from '@/utils/graphLayout'
import type { PositionedNode } from '@/utils/graphLayout'

export type { BoardFlowEdge }

const props = defineProps<{
  asset: { id: number; name: string; code: string; dataType?: string } | null
  canvasEndpointIds: number[]
  allEndpoints: EndpointOption[]
  executors: ExecutorOption[]
  flowDetailsById: Record<number, FlowDetail>
  draft: FlowDetail | null
  edges: BoardFlowEdge[]
  selectedEdgeId?: string | null
}>()

const emit = defineEmits<{
  selectEdge: [edgeId: string | null]
  connect: [sourceEndpointId: number, targetEndpointId: number]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let graph: Graph | null = null
let renderSeq = 0
let structureKey = ''

function parseBoardNodeId(cellId: string): number | null {
  const m = /^ep-(\d+)$/.exec(cellId)
  return m ? Number(m[1]) : null
}

function edgeLabelText(edge: BoardFlowEdge): string {
  const purpose = edge.draft ? '未保存' : purposeLabel(edge.purpose)
  const count = edge.pathCount ?? 0
  if (!edge.draft && count > 1) return `${purpose} · ${count}路径`
  return purpose
}

function destroyGraph() {
  if (graph) {
    try {
      graph.off()
      graph.dispose()
    } catch {
      // ignore dispose races during rapid refresh
    }
    graph = null
  }
  if (containerRef.value) {
    containerRef.value.innerHTML = ''
  }
}

function computeStructureKey() {
  const ep = [...props.canvasEndpointIds].sort((a, b) => a - b).join(',')
  const edges = props.edges
    .map(
      (e) =>
        `${e.id}:${e.sourceEndpointId}>${e.targetEndpointId}:${e.purpose}:${e.primary}:${e.pathCount ?? 0}:${e.draft ? 1 : 0}`,
    )
    .sort()
    .join('|')
  const details = Object.entries(props.flowDetailsById)
    .map(([id, d]) => {
      const paths = d.paths
        .map(
          (p) =>
            `${p.name}:${p.enabled}:${p.sortOrder}:${p.steps.map((s) => `${s.seq}:${s.executorId}:${s.hostId}`).join(',')}`,
        )
        .join(';')
      return `${id}=${paths}`
    })
    .sort()
    .join('|')
  const draftKey = props.draft
    ? props.draft.paths
        .map(
          (p) =>
            `${p.name}:${p.steps.map((s) => `${s.seq}:${s.executorId}:${s.hostId}`).join(',')}`,
        )
        .join(';')
    : ''
  return `${ep}#${edges}#${details}#${draftKey}`
}

const CONNECT_SNAP_RADIUS = 64

function buildGraphInstance() {
  if (!containerRef.value) return
  destroyGraph()

  graph = new Graph({
    container: containerRef.value,
    autoResize: true,
    panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheelDown'] },
    mousewheel: {
      enabled: true,
      modifiers: null,
      factor: 1.12,
      minScale: 0.2,
      maxScale: 3,
      zoomAtMousePosition: true,
    },
    background: { color: '#f4f7f5' },
    grid: { visible: true, type: 'dot', args: { color: '#d7e0db', thickness: 1 } },
    connecting: {
      allowBlank: false,
      allowLoop: false,
      allowNode: false,
      allowEdge: false,
      allowPort: true,
      highlight: true,
      snap: { radius: CONNECT_SNAP_RADIUS, anchor: 'center' },
      connectionPoint: 'anchor',
      connector: { name: 'rounded', args: { radius: 8 } },
      router: { name: 'orth', args: { padding: 12 } },
      createEdge() {
        return graph!.createEdge({
          attrs: {
            line: {
              stroke: '#ea580c',
              strokeWidth: 2,
              strokeDasharray: '6 4',
              targetMarker: { name: 'block', width: 10, height: 7 },
            },
          },
          data: { provisional: true },
        })
      },
      validateMagnet({ magnet }) {
        return magnet.getAttribute('port-group') === 'out'
      },
      validateConnection({ sourceCell, targetCell, targetMagnet }) {
        if (!sourceCell?.isNode() || !targetCell?.isNode()) return false
        if (sourceCell.id === targetCell.id) return false
        if (targetMagnet?.getAttribute('port-group') !== 'in') return false
        return isBoardEndpointNode(String(sourceCell.id)) && isBoardEndpointNode(String(targetCell.id))
      },
    },
    highlighting: {
      magnetAvailable: {
        name: 'stroke',
        args: {
          attrs: {
            fill: '#ecfdf5',
            stroke: '#16a34a',
            strokeWidth: 3,
          },
          padding: 6,
        },
      },
      magnetAdsorbed: {
        name: 'stroke',
        args: {
          attrs: {
            fill: '#dcfce7',
            stroke: '#15803d',
            strokeWidth: 4,
          },
          padding: 8,
        },
      },
    },
  })

  graph.on('edge:connected', ({ edge, isNew }) => {
    if (!isNew) return
    const sourceId = parseBoardNodeId(String(edge.getSourceCellId()))
    const targetId = parseBoardNodeId(String(edge.getTargetCellId()))
    edge.remove()
    if (sourceId == null || targetId == null) return
    emit('connect', sourceId, targetId)
  })

  graph.on('edge:click', ({ edge }) => {
    const data = edge.getData() as GraphEdge | { provisional?: boolean; kind?: string } | undefined
    if (!data || 'provisional' in data || ('kind' in data && data.kind === 'relation')) return
    const flowEdge = data as GraphEdge
    emit('selectEdge', boardFlowIdFromCell(String(flowEdge.id ?? edge.id)))
  })

  graph.on('blank:click', () => emit('selectEdge', null))
  graph.on('node:click', () => emit('selectEdge', null))
}

function endpointPorts(endpointId: number) {
  if (!props.canvasEndpointIds.includes(endpointId)) return undefined
  return {
    groups: {
      out: {
        position: 'right',
        attrs: {
          circle: {
            r: 9,
            magnet: true,
            stroke: '#1d4ed8',
            fill: '#fff',
            strokeWidth: 2,
            cursor: 'crosshair',
          },
        },
      },
      in: {
        position: 'left',
        attrs: {
          circle: {
            r: 9,
            magnet: true,
            stroke: '#15803d',
            fill: '#fff',
            strokeWidth: 2,
            cursor: 'crosshair',
          },
        },
      },
    },
    items: [
      { id: 'out', group: 'out' },
      { id: 'in', group: 'in' },
    ],
  }
}

function paintNodes(positioned: PositionedNode[]) {
  if (!graph) return

  const containers = positioned.filter((n) => n.isContainer)
  const leaves = positioned.filter((n) => !n.isContainer)

  containers.forEach((node) => {
    const w = node.width ?? NODE_WIDTH
    const h = node.height ?? NODE_HEIGHT
    graph!.addNode({
      id: node.id,
      x: node.x - w / 2,
      y: node.y - h / 2,
      width: w,
      height: h,
      shape: 'rect',
      zIndex: 0,
      movable: false,
      markup: [
        { tagName: 'rect', selector: 'body' },
        { tagName: 'rect', selector: 'header' },
        { tagName: 'rect', selector: 'headerFlat' },
        { tagName: 'text', selector: 'label' },
        { tagName: 'text', selector: 'hint' },
      ],
      attrs: {
        body: {
          stroke: '#64748b',
          strokeWidth: 1.4,
          fill: '#f1f5f9',
          rx: 12,
          ry: 12,
        },
        header: {
          refWidth: '100%',
          height: 36,
          stroke: 'none',
          fill: '#dbe4ee',
          rx: 12,
          ry: 12,
        },
        headerFlat: {
          refWidth: '100%',
          y: 12,
          height: 24,
          stroke: 'none',
          fill: '#dbe4ee',
        },
        label: {
          text: node.label,
          fill: '#0f172a',
          fontSize: 13,
          fontWeight: 600,
          fontFamily: 'IBM Plex Sans, sans-serif',
          refX: 14,
          refY: 18,
          textAnchor: 'start',
          textVerticalAnchor: 'middle',
        },
        hint: {
          text: node.type === 'HOST' ? '主机' : 'Kafka 集群',
          fill: '#64748b',
          fontSize: 11,
          fontFamily: 'IBM Plex Sans, sans-serif',
          refX: '100%',
          refX2: -12,
          refY: 18,
          textAnchor: 'end',
          textVerticalAnchor: 'middle',
        },
      },
      data: node,
    })
  })

  leaves.forEach((node) => {
    const isExecutor = node.kind === 'EXECUTOR'
    const isHost = node.type === 'HOST'
    const isBrokerChip = node.nestRole === 'broker'
    const isTopicInner = node.nestRole === 'topic'
    const w = node.width ?? NODE_WIDTH
    const h = node.height ?? NODE_HEIGHT
    const endpointId = node.endpointId ?? null

    const child = graph!.addNode({
      id: node.id,
      x: node.x - w / 2,
      y: node.y - h / 2,
      width: w,
      height: h,
      shape: 'rect',
      zIndex: 2,
      movable: false,
      ports: endpointId != null ? endpointPorts(endpointId) : undefined,
      attrs: {
        body: {
          stroke: isExecutor
            ? '#b45309'
            : isBrokerChip
              ? '#64748b'
              : isTopicInner
                ? '#1d4ed8'
                : isHost
                  ? '#64748b'
                  : '#1f4f46',
          strokeWidth: isExecutor ? 2 : isBrokerChip ? 1 : 1.4,
          fill: isExecutor
            ? '#fff7ed'
            : isBrokerChip
              ? '#ffffff'
              : isTopicInner
                ? '#ffffff'
                : isHost
                  ? '#f8fafc'
                  : '#ffffff',
          rx: isExecutor ? 28 : isBrokerChip ? 8 : 10,
          ry: isExecutor ? 28 : isBrokerChip ? 8 : 10,
        },
        label: {
          text: isExecutor
            ? `${node.label}\n程序`
            : isBrokerChip
              ? node.label
              : `${node.label}\n${endpointTypeLabel(node.type)}`,
          fill: '#0f172a',
          fontSize: isBrokerChip ? 11 : 12,
          fontFamily: 'IBM Plex Sans, sans-serif',
          textWrap: {
            width: w - (isBrokerChip ? 8 : 16),
            height: h - (isBrokerChip ? 6 : 12),
            ellipsis: true,
          },
        },
      },
      data: node,
    })

    if (node.parentNodeId) {
      const parentCell = graph!.getCellById(node.parentNodeId)
      if (parentCell?.isNode()) {
        parentCell.addChild(child)
      }
    }
  })
}

function paintFlowEdges(viewGraph: AssetGraph) {
  if (!graph) return

  const boardEdgeById = new Map(props.edges.map((e) => [e.id, e]))

  expandDisplayEdges(viewGraph).forEach((edge) => {
    const flowId = boardFlowIdFromCell(edge.id)
    const boardEdge = boardEdgeById.get(flowId)
    const selected = props.selectedEdgeId === flowId
    const isDraft = boardEdge?.draft

    let stroke = edgeStroke(edge)
    let strokeWidth = edge.primary ? 2.5 : 1.5
    let dash: string | undefined = edge.primary ? undefined : '6 4'

    if (isDraft) {
      stroke = '#ea580c'
      strokeWidth = selected ? 3 : 2
      dash = '6 4'
    } else if (selected) {
      strokeWidth = 3
    }

    graph!.addEdge({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      labels: [
        {
          attrs: {
            label: {
              text: isDraft && edge.id === flowId ? edgeLabelText(boardEdge!) : edge.label,
              fill: '#0f172a',
              fontSize: 11,
              fontFamily: 'IBM Plex Sans, sans-serif',
              pointerEvents: 'none',
            },
            body: {
              fill: isDraft ? '#fff7ed' : '#ecf4f1',
              stroke: isDraft ? '#ea580c' : stroke,
              strokeWidth: selected ? 1.5 : 1,
              rx: 4,
              ry: 4,
              pointerEvents: 'none',
            },
          },
          position: 0.5,
        },
      ],
      attrs: {
        line: {
          stroke,
          strokeWidth,
          strokeDasharray: dash,
          fill: 'none',
          targetMarker: {
            name: 'block',
            width: 10,
            height: 8,
            offset: 0,
            fill: stroke,
            stroke,
            strokeWidth: 1,
          },
        },
      },
      router: { name: 'orth', args: { padding: 12 } },
      connector: { name: 'rounded', args: { radius: 8 } },
      data: edge.flowEdge,
      zIndex: selected ? 20 : 1,
    })
  })
}

function paintRelations(viewGraph: AssetGraph) {
  if (!graph) return

  visibleRelations(viewGraph.relations, 'compact').forEach((rel) => {
    const isRunsOn = rel.type === 'RUNS_ON'
    const stroke = isRunsOn ? '#c2410c' : '#94a3b8'
    graph!.addEdge({
      id: rel.id,
      source: rel.source,
      target: rel.target,
      labels: [
        {
          attrs: {
            label: {
              text: rel.label || rel.type,
              fill: isRunsOn ? '#b45309' : '#64748b',
              fontSize: 10,
              fontFamily: 'IBM Plex Sans, sans-serif',
              pointerEvents: 'none',
            },
            body: {
              fill: isRunsOn ? '#fff7ed' : '#f8fafc',
              stroke: isRunsOn ? '#fdba74' : '#cbd5e1',
              strokeWidth: 1,
              rx: 3,
              ry: 3,
              pointerEvents: 'none',
            },
          },
          position: 0.5,
        },
      ],
      attrs: {
        line: {
          stroke,
          strokeWidth: isRunsOn ? 1.8 : 1.2,
          strokeDasharray: '4 4',
          fill: 'none',
          targetMarker: {
            name: 'block',
            width: 8,
            height: 6,
            fill: stroke,
            stroke,
            strokeWidth: 1,
          },
        },
      },
      router: { name: 'orth', args: { padding: 10 } },
      connector: { name: 'rounded', args: { radius: 6 } },
      data: { kind: 'relation', ...rel },
      zIndex: 0,
    })
  })
}

async function paintCells(fit: boolean) {
  if (!graph || !props.asset) return
  const seq = ++renderSeq

  const assetGraph = buildBoardAssetGraph({
    asset: props.asset,
    canvasEndpointIds: new Set(props.canvasEndpointIds),
    allEndpoints: props.allEndpoints,
    boardEdges: props.edges,
    flowDetailsById: props.flowDetailsById,
    draft: props.draft,
    executors: props.executors,
  })

  if (!assetGraph.nodes.length) {
    if (seq === renderSeq && fit) graph.zoomToFit({ padding: 48, maxScale: 1.1 })
    return
  }

  const positioned = await layoutGraph(assetGraph, 'compact')
  if (seq !== renderSeq || !graph) return

  graph.batchUpdate(() => {
    const existing = graph!.getCells()
    if (existing.length) graph!.removeCells(existing)
    paintNodes(positioned)
    paintFlowEdges(assetGraph)
    paintRelations(assetGraph)
  })

  if (seq !== renderSeq) return
  if (fit) graph.zoomToFit({ padding: 40, maxScale: 1.2 })
}

function applyEdgeSelectionStyles() {
  if (!graph) return
  const boardEdgeById = new Map(props.edges.map((e) => [e.id, e]))

  graph.getEdges().forEach((edge) => {
    const data = edge.getData() as GraphEdge | { kind?: string } | undefined
    if (!data || ('kind' in data && data.kind === 'relation')) return

    const flowId = boardFlowIdFromCell(String(edge.id))
    const boardEdge = boardEdgeById.get(flowId)
    const selected = props.selectedEdgeId === flowId
    const isDraft = boardEdge?.draft

    const flowEdge = data as GraphEdge
    let stroke = edgeStroke(flowEdge)
    let strokeWidth = flowEdge.primary ? 2.5 : 1.5
    let dash: string | undefined = flowEdge.primary ? undefined : '6 4'

    if (isDraft) {
      stroke = '#ea580c'
      strokeWidth = selected ? 3 : 2
      dash = '6 4'
    } else if (selected) {
      strokeWidth = 3
    }

    edge.setAttrs({
      line: {
        stroke,
        strokeWidth,
        strokeDasharray: dash,
        targetMarker: {
          name: 'block',
          width: 10,
          height: 8,
          offset: 0,
          fill: stroke,
          stroke,
          strokeWidth: 1,
        },
      },
    })
    edge.setZIndex(selected ? 20 : 1)
  })
}

function render(forceRebuild = false) {
  if (!containerRef.value || !props.asset) return
  const nextKey = computeStructureKey()
  const layoutChanged = forceRebuild || nextKey !== structureKey
  structureKey = nextKey

  if (!graph || forceRebuild) {
    buildGraphInstance()
  }

  if (layoutChanged || forceRebuild) {
    void paintCells(true)
  }
}

function zoomToFit() {
  graph?.zoomToFit({ padding: 40, maxScale: 1.2 })
}

function zoomIn() {
  graph?.zoom(0.2)
}

function zoomOut() {
  graph?.zoom(-0.2)
}

function reset() {
  structureKey = ''
  render(true)
}

defineExpose({ zoomToFit, zoomIn, zoomOut, render, reset })

watch(
  () =>
    [
      props.asset,
      props.canvasEndpointIds,
      props.allEndpoints,
      props.executors,
      props.flowDetailsById,
      props.draft,
      props.edges,
    ] as const,
  () => render(false),
  { deep: true },
)

watch(
  () => props.selectedEdgeId,
  () => {
    if (!graph) {
      render(true)
      return
    }
    applyEdgeSelectionStyles()
  },
)

onMounted(() => render(true))
onBeforeUnmount(() => {
  destroyGraph()
})
</script>

<template>
  <div class="board-wrap">
    <div class="zoom-controls">
      <button type="button" aria-label="放大" @click="zoomIn">+</button>
      <button type="button" aria-label="缩小" @click="zoomOut">−</button>
      <button type="button" aria-label="适配画布" @click="zoomToFit">⌂</button>
    </div>
    <div ref="containerRef" class="canvas" />
    <div class="hint">
      布局与一键成图一致：源 → 程序 → 目标。拖线靠近落点端口会自动<strong>吸附</strong>；编辑 A→B 时再拖 B→C 会新增独立流向
    </div>
  </div>
</template>

<style scoped>
.board-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 520px;
  border: 1px solid #d5e0db;
  border-radius: 12px;
  overflow: hidden;
  background: #f4f7f5;
}

.canvas {
  width: 100%;
  height: 100%;
}

.hint {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 2;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #d5e0db;
  color: #64748b;
  font-size: 12px;
  pointer-events: none;
}

.zoom-controls {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.zoom-controls button {
  width: 44px;
  height: 44px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.95);
  color: #0f172a;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.zoom-controls button:active {
  background: #ecf4f1;
}

:deep(.x6-port-body) {
  transition: filter 0.12s ease;
}

:deep(.x6-port-body:hover) {
  filter: drop-shadow(0 0 4px rgba(29, 78, 216, 0.45));
}
</style>
