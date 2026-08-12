<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Graph } from '@antv/x6'
import type { EndpointOption } from '@/types/flow'
import { purposeLabel } from '@/types/flow'
import {
  boardNodeId,
  layoutBoardEndpoints,
  parseBoardNodeId,
} from '@/utils/flowBoardLayout'
import { edgeStroke } from '@/utils/graphLayout'

export interface BoardFlowEdge {
  id: string
  flowId?: number | null
  sourceEndpointId: number
  targetEndpointId: number
  purpose: string
  primary: boolean
  draft?: boolean
}

const props = defineProps<{
  endpoints: EndpointOption[]
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

function nodeFill(type: string): string {
  if (type.includes('TOPIC')) return '#eff6ff'
  if (type === 'DIRECTORY' || type === 'OBJECT_PREFIX') return '#f0fdf4'
  if (type === 'HOST') return '#f8fafc'
  if (type === 'KAFKA' || type === 'ROCKETMQ') return '#eef2ff'
  return '#ffffff'
}

function nodeStroke(type: string): string {
  if (type.includes('TOPIC')) return '#1d4ed8'
  if (type === 'DIRECTORY' || type === 'OBJECT_PREFIX') return '#15803d'
  if (type === 'HOST') return '#64748b'
  if (type === 'KAFKA' || type === 'ROCKETMQ') return '#4338ca'
  return '#1f4f46'
}

function render() {
  if (!containerRef.value || !graph) return
  const seq = ++renderSeq
  const layouts = layoutBoardEndpoints(props.endpoints)
  const layoutIds = new Set(layouts.map((n) => n.id))

  graph.clearCells()

  layouts.forEach((n) => {
    graph!.addNode({
      id: boardNodeId(n.id),
      x: n.x - n.width / 2,
      y: n.y - n.height / 2,
      width: n.width,
      height: n.height,
      shape: 'rect',
      attrs: {
        body: {
          fill: nodeFill(n.type),
          stroke: nodeStroke(n.type),
          strokeWidth: 1.5,
          rx: 10,
          ry: 10,
        },
        label: {
          text: `${n.label}\n${n.typeLabel}`,
          fill: '#0f172a',
          fontSize: 12,
          fontFamily: 'IBM Plex Sans, sans-serif',
          textWrap: { width: n.width - 16, height: n.height - 10, ellipsis: true },
        },
      },
      ports: {
        groups: {
          out: {
            position: 'right',
            attrs: {
              circle: { r: 5, magnet: true, stroke: '#1d4ed8', fill: '#fff', strokeWidth: 1.5 },
            },
          },
          in: {
            position: 'left',
            attrs: {
              circle: { r: 5, magnet: true, stroke: '#15803d', fill: '#fff', strokeWidth: 1.5 },
            },
          },
        },
        items: [
          { id: 'out', group: 'out' },
          { id: 'in', group: 'in' },
        ],
      },
      data: { endpointId: n.id, breadcrumb: n.breadcrumb },
    })
  })

  props.edges.forEach((edge) => {
    if (!layoutIds.has(edge.sourceEndpointId) || !layoutIds.has(edge.targetEndpointId)) return
    const selected = props.selectedEdgeId === edge.id
    const stroke = edge.draft
      ? '#ea580c'
      : edgeStroke({ primary: edge.primary, purpose: edge.purpose, upstream: false })
    graph!.addEdge({
      id: edge.id,
      source: { cell: boardNodeId(edge.sourceEndpointId), port: 'out' },
      target: { cell: boardNodeId(edge.targetEndpointId), port: 'in' },
      labels: [
        {
          attrs: {
            label: {
              text: edge.draft ? '未保存' : purposeLabel(edge.purpose),
              fill: '#0f172a',
              fontSize: 11,
              fontFamily: 'IBM Plex Sans, sans-serif',
            },
            body: {
              fill: '#fff',
              stroke: selected ? '#0f3d36' : '#cbd5e1',
              strokeWidth: selected ? 1.5 : 1,
              rx: 4,
              ry: 4,
            },
          },
          position: 0.5,
        },
      ],
      attrs: {
        line: {
          stroke,
          strokeWidth: selected ? 3 : edge.primary ? 2.2 : 1.6,
          strokeDasharray: edge.draft ? '6 4' : edge.primary ? undefined : '6 4',
          targetMarker: { name: 'block', width: 10, height: 7 },
        },
      },
      router: { name: 'orth', args: { padding: 16 } },
      connector: { name: 'rounded', args: { radius: 8 } },
      data: edge,
      zIndex: selected ? 20 : 1,
    })
  })

  if (seq !== renderSeq) return
  if (layouts.length) {
    graph.zoomToFit({ padding: 48, maxScale: 1.1 })
  }
}

function initGraph() {
  if (!containerRef.value) return
  graph?.dispose()
  graph = new Graph({
    container: containerRef.value,
    autoResize: true,
    panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheelDown'] },
    mousewheel: { enabled: true, modifiers: null, factor: 1.1, minScale: 0.3, maxScale: 2.5 },
    background: { color: '#f4f7f5' },
    grid: { visible: true, type: 'dot', args: { color: '#d7e0db', thickness: 1 } },
    connecting: {
      allowBlank: false,
      allowLoop: false,
      allowNode: false,
      allowEdge: false,
      allowPort: true,
      highlight: true,
      snap: { radius: 24 },
      connector: { name: 'rounded', args: { radius: 8 } },
      router: { name: 'orth', args: { padding: 16 } },
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
        return true
      },
    },
    highlighting: {
      magnetAvailable: {
        name: 'stroke',
        args: { attrs: { fill: '#fff', stroke: '#16a34a', strokeWidth: 3 } },
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
    const data = edge.getData() as BoardFlowEdge | { provisional?: boolean } | undefined
    if (!data || 'provisional' in data) return
    emit('selectEdge', edge.id)
  })

  graph.on('blank:click', () => emit('selectEdge', null))
  graph.on('node:click', () => emit('selectEdge', null))

  render()
}

function zoomToFit() {
  graph?.zoomToFit({ padding: 48, maxScale: 1.1 })
}

defineExpose({ zoomToFit, render })

watch(
  () => [props.endpoints, props.edges, props.selectedEdgeId] as const,
  () => render(),
  { deep: true },
)

onMounted(initGraph)
onBeforeUnmount(() => {
  graph?.dispose()
  graph = null
})
</script>

<template>
  <div class="board-wrap">
    <div ref="containerRef" class="canvas" />
    <div class="hint">从落点右侧圆点拖线到另一落点左侧圆点，即可创建流向</div>
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
</style>
