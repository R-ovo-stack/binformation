<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Graph, Export } from '@antv/x6'
import type { AssetGraph, GraphEdge } from '@/types/graph'
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  edgeStroke,
  endpointTypeLabel,
  expandDisplayEdges,
  filterGraphForMode,
  layoutGraph,
  visibleRelations,
  type LayoutMode,
} from '@/utils/graphLayout'

const props = defineProps<{
  graph: AssetGraph | null
  layoutMode?: LayoutMode
}>()

const emit = defineEmits<{
  selectEdge: [edge: GraphEdge | null]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const error = ref('')
let graphInstance: Graph | null = null

const mode = computed<LayoutMode>(() => props.layoutMode || 'compact')

const viewGraph = computed(() => {
  if (!props.graph) return null
  return filterGraphForMode(props.graph, mode.value)
})

const edgeMap = computed(() => {
  const map = new Map<string, GraphEdge>()
  const g = viewGraph.value
  if (!g) return map
  expandDisplayEdges(g).forEach((edge) => map.set(edge.id, edge.flowEdge))
  g.edges.forEach((edge) => map.set(edge.id, edge))
  return map
})

let renderSeq = 0

async function render() {
  const g = viewGraph.value
  const currentMode = mode.value
  if (!containerRef.value || !g) return
  const seq = ++renderSeq
  loading.value = true
  error.value = ''
  try {
    if (graphInstance) {
      graphInstance.dispose()
      graphInstance = null
    }

    graphInstance = new Graph({
      container: containerRef.value,
      autoResize: true,
      interacting: {
        nodeMovable: true,
        edgeMovable: false,
        arrowheadMovable: false,
        vertexMovable: false,
        vertexAddable: false,
        vertexDeletable: false,
      },
      panning: {
        enabled: true,
        eventTypes: ['leftMouseDown', 'mouseWheelDown'],
      },
      mousewheel: {
        enabled: true,
        modifiers: null,
        factor: 1.12,
        minScale: 0.2,
        maxScale: 3,
        zoomAtMousePosition: true,
      },
      background: {
        color: '#f4f7f5',
      },
      grid: {
        visible: true,
        type: 'dot',
        args: {
          color: '#d7e0db',
          thickness: 1,
        },
      },
      connecting: {
        router: {
          name: 'orth',
          args: { padding: 12 },
        },
        connector: {
          name: 'rounded',
          args: { radius: 8 },
        },
      },
    })
    graphInstance.use(new Export())
    containerRef.value.style.touchAction = 'none'

    graphInstance.on('edge:click', ({ edge }) => {
      const data = edge.getData() as { kind?: string } | GraphEdge | undefined
      if (data && 'kind' in data && data.kind === 'relation') {
        emit('selectEdge', null)
        return
      }
      const flow =
        (data as GraphEdge | undefined)
        ?? edgeMap.value.get(String(edge.id))
        ?? null
      emit('selectEdge', flow)
    })
    graphInstance.on('blank:click', () => emit('selectEdge', null))
    graphInstance.on('node:click', () => emit('selectEdge', null))

    const positioned = await layoutGraph(g, currentMode)
    if (seq !== renderSeq || !graphInstance) return

    const byId = new Map(positioned.map((n) => [n.id, n]))

    // 先放大框（容器），再加子节点并挂到父节点下
    const containers = positioned.filter((n) => n.isContainer)
    const leaves = positioned.filter((n) => !n.isContainer)

    containers.forEach((node) => {
      const w = node.width ?? NODE_WIDTH
      const h = node.height ?? NODE_HEIGHT
      graphInstance!.addNode({
        id: node.id,
        x: node.x - w / 2,
        y: node.y - h / 2,
        width: w,
        height: h,
        shape: 'rect',
        zIndex: 0,
        attrs: {
          body: {
            stroke: '#1d4ed8',
            strokeWidth: 1.5,
            strokeDasharray: '6 4',
            fill: 'rgba(239, 246, 255, 0.72)',
            rx: 14,
            ry: 14,
          },
          label: {
            text: `${node.label}  ·  ${endpointTypeLabel(node.type)}`,
            fill: '#1e3a8a',
            fontSize: 12,
            fontFamily: 'IBM Plex Sans, sans-serif',
            refX: 14,
            refY: 14,
            textAnchor: 'start',
            textVerticalAnchor: 'top',
          },
        },
        data: node,
      })
    })

    leaves.forEach((node) => {
      const isExecutor = node.kind === 'EXECUTOR'
      const isHost = node.type === 'HOST'
      const w = node.width ?? NODE_WIDTH
      const h = node.height ?? NODE_HEIGHT
      const parent = node.parentNodeId ? byId.get(node.parentNodeId) : null

      let x = node.x - w / 2
      let y = node.y - h / 2
      // X6 子节点坐标相对父节点左上角
      if (parent?.isContainer) {
        const pw = parent.width ?? NODE_WIDTH
        const ph = parent.height ?? NODE_HEIGHT
        x = node.x - w / 2 - (parent.x - pw / 2)
        y = node.y - h / 2 - (parent.y - ph / 2)
      }

      const child = graphInstance!.addNode({
        id: node.id,
        x,
        y,
        width: w,
        height: h,
        shape: 'rect',
        zIndex: 2,
        attrs: {
          body: {
            stroke: isExecutor ? '#b45309' : isHost ? '#64748b' : '#1f4f46',
            strokeWidth: isExecutor ? 2 : 1.5,
            fill: isExecutor ? '#fff7ed' : isHost ? '#f8fafc' : '#ffffff',
            rx: isExecutor ? 28 : 10,
            ry: isExecutor ? 28 : 10,
          },
          label: {
            text: isExecutor
              ? `${node.label}\n程序`
              : `${node.label}\n${endpointTypeLabel(node.type)}`,
            fill: '#0f172a',
            fontSize: 12,
            fontFamily: 'IBM Plex Sans, sans-serif',
            textWrap: {
              width: w - 16,
              height: h - 12,
              ellipsis: true,
            },
          },
        },
        data: node,
      })

      if (node.parentNodeId) {
        const parentCell = graphInstance!.getCellById(node.parentNodeId)
        if (parentCell && parentCell.isNode()) {
          parentCell.addChild(child)
        }
      }
    })

    expandDisplayEdges(g).forEach((edge) => {
      const stroke = edgeStroke(edge)
      graphInstance!.addEdge({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        labels: [
          {
            attrs: {
              label: {
                text: edge.label,
                fill: '#0f172a',
                fontSize: 11,
                fontFamily: 'IBM Plex Sans, sans-serif',
                pointerEvents: 'none',
              },
              body: {
                fill: '#ecf4f1',
                stroke: stroke,
                strokeWidth: 1,
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
            strokeWidth: edge.primary ? 2.5 : 1.5,
            strokeDasharray: edge.primary ? undefined : '6 4',
            fill: 'none',
            targetMarker: {
              name: 'block',
              width: 10,
              height: 8,
              offset: 0,
              fill: stroke,
              stroke: stroke,
              strokeWidth: 1,
            },
          },
        },
        router: { name: 'orth', args: { padding: 12 } },
        connector: { name: 'rounded', args: { radius: 8 } },
        data: edge.flowEdge,
        zIndex: 1,
      })
    })

    visibleRelations(g.relations, currentMode).forEach((rel) => {
      const isRunsOn = rel.type === 'RUNS_ON'
      const stroke = isRunsOn
        ? '#c2410c'
        : rel.type === 'BROKER_OF'
          ? '#64748b'
          : rel.type === 'CONTAINS'
            ? '#1d4ed8'
            : '#94a3b8'
      graphInstance!.addEdge({
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
              stroke: stroke,
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

    if (seq !== renderSeq || !graphInstance) return
    graphInstance.zoomToFit({ padding: 40, maxScale: 1.2 })
  } catch (e) {
    if (seq !== renderSeq) return
    error.value = e instanceof Error ? e.message : '成图失败'
  } finally {
    if (seq === renderSeq) loading.value = false
  }
}

function zoomToFit() {
  graphInstance?.zoomToFit({ padding: 40, maxScale: 1.2 })
}

function zoomIn() {
  graphInstance?.zoom(0.2)
}

function zoomOut() {
  graphInstance?.zoom(-0.2)
}

function exportPng() {
  if (!graphInstance) return
  graphInstance.toPNG(
    (dataUri) => {
      const link = document.createElement('a')
      link.download = `${props.graph?.assetCode ?? 'flow'}-graph.png`
      link.href = dataUri
      link.click()
    },
    {
      backgroundColor: '#f4f7f5',
      padding: 24,
      quality: 1,
    },
  )
}

defineExpose({ zoomToFit, zoomIn, zoomOut, exportPng, render })

watch(
  () => [props.graph, props.layoutMode] as const,
  () => {
    void render()
  },
)

onMounted(() => {
  void render()
})

onBeforeUnmount(() => {
  graphInstance?.dispose()
  graphInstance = null
})
</script>

<template>
  <div class="canvas-wrap">
    <div v-if="loading" class="overlay">正在一键成图…</div>
    <div v-if="error" class="overlay error">{{ error }}</div>
    <div class="zoom-controls">
      <button type="button" aria-label="放大" @click="zoomIn">+</button>
      <button type="button" aria-label="缩小" @click="zoomOut">−</button>
      <button type="button" aria-label="适配画布" @click="zoomToFit">⌂</button>
    </div>
    <div ref="containerRef" class="canvas" />
  </div>
</template>

<style scoped>
.canvas-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 520px;
  border: 1px solid #d5e0db;
  border-radius: 12px;
  overflow: hidden;
  background: #f4f7f5;
  touch-action: none;
}

.canvas {
  width: 100%;
  height: 100%;
  touch-action: none;
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
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.zoom-controls button:active {
  background: #ecf4f1;
}

.overlay {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(244, 247, 245, 0.72);
  color: #0f172a;
  font-size: 14px;
}

.overlay.error {
  color: #b91c1c;
}
</style>
