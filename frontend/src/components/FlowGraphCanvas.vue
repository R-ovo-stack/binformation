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
        // 包含关系固化：只能拖集群卡片/独立节点，禁止拖内部主题与 Broker
        nodeMovable(view) {
          const cell = view.cell
          if (cell.getParent()) return false
          const data = cell.getData() as { parentNodeId?: string | null } | undefined
          if (data?.parentNodeId) return false
          return true
        },
        edgeMovable: false,
        arrowheadMovable: false,
        vertexMovable: false,
        vertexAddable: false,
        vertexDeletable: false,
      },
      embedding: {
        enabled: false,
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
        movable: true,
        markup: [
          { tagName: 'rect', selector: 'body' },
          { tagName: 'rect', selector: 'header' },
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
            text: 'Kafka 集群',
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

      // 必须用绝对坐标 addNode，再 addChild，由 X6 转为相对坐标。
      // 若先传相对坐标再 addChild，会被二次换算，Broker 会掉到卡片外。
      const x = node.x - w / 2
      const y = node.y - h / 2
      const lockedInside = !!node.parentNodeId

      const child = graphInstance!.addNode({
        id: node.id,
        x,
        y,
        width: w,
        height: h,
        shape: 'rect',
        zIndex: 2,
        movable: !lockedInside,
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
        const parentCell = graphInstance!.getCellById(node.parentNodeId)
        if (parentCell && parentCell.isNode()) {
          parentCell.addChild(child)
          child.prop('movable', false)
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
