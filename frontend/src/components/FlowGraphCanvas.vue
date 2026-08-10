<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Graph, Export } from '@antv/x6'
import type { AssetGraph, GraphEdge } from '@/types/graph'
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  edgeStroke,
  endpointTypeLabel,
  layoutGraph,
  purposeLabel,
} from '@/utils/graphLayout'

const props = defineProps<{
  graph: AssetGraph | null
}>()

const emit = defineEmits<{
  selectEdge: [edge: GraphEdge | null]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const error = ref('')
let graphInstance: Graph | null = null

const edgeMap = computed(() => {
  const map = new Map<string, GraphEdge>()
  props.graph?.edges.forEach((edge) => map.set(edge.id, edge))
  return map
})

async function render() {
  if (!containerRef.value || !props.graph) return
  loading.value = true
  error.value = ''
  try {
    if (!graphInstance) {
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
          // 空白处拖动画布；点在节点上则拖节点
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
        // 默认用 orth，拖动时比 manhattan 更稳，减少箭头黑三角
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
    }

    const positioned = await layoutGraph(props.graph)
    graphInstance.clearCells()

    positioned.forEach((node) => {
      const isExecutor = node.kind === 'EXECUTOR'
      const isBroker = node.type === 'HOST'
      const isKafka = node.type === 'KAFKA'
      graphInstance!.addNode({
        id: node.id,
        x: node.x - NODE_WIDTH / 2,
        y: node.y - NODE_HEIGHT / 2,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
        // 统一圆角矩形，避免 ellipse 锚点导致箭头出现黑色三角
        shape: 'rect',
        attrs: {
          body: {
            stroke: isExecutor ? '#b45309' : isKafka ? '#1d4ed8' : isBroker ? '#64748b' : '#1f4f46',
            strokeWidth: isExecutor ? 2 : 1.5,
            fill: isExecutor ? '#fff7ed' : isBroker ? '#f8fafc' : '#ffffff',
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
              width: NODE_WIDTH - 16,
              height: NODE_HEIGHT - 12,
              ellipsis: true,
            },
          },
        },
        data: node,
      })
    })

    props.graph.edges.forEach((edge) => {
      const stroke = edgeStroke(edge)
      graphInstance!.addEdge({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        labels: [
          {
            attrs: {
              label: {
                text: purposeLabel(edge.purpose),
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
            // 必须 none，否则折线区域会被填成黑色三角/多边形
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
        router: {
          name: 'orth',
          args: { padding: 12 },
        },
        connector: {
          name: 'rounded',
          args: { radius: 8 },
        },
        data: edge,
        zIndex: 1,
      })
    })

    ;(props.graph.relations ?? []).forEach((rel) => {
      const isRunsOn = rel.type === 'RUNS_ON'
      const isVia = rel.type === 'VIA_EXECUTOR'
      const stroke = isRunsOn ? '#c2410c' : isVia ? '#a8a29e' : '#94a3b8'
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
            strokeDasharray: isVia ? '2 4' : '4 4',
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
        router: {
          name: 'orth',
          args: { padding: 8 },
        },
        connector: {
          name: 'rounded',
          args: { radius: 6 },
        },
        data: { kind: 'relation', ...rel },
        zIndex: 0,
      })
    })

    graphInstance.zoomToFit({ padding: 40, maxScale: 1.2 })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '成图失败'
  } finally {
    loading.value = false
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
  () => props.graph,
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
