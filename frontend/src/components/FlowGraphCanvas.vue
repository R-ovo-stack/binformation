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
        panning: true,
        mousewheel: {
          enabled: true,
          modifiers: ['ctrl', 'meta'],
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
          router: 'manhattan',
          connector: {
            name: 'rounded',
            args: { radius: 8 },
          },
        },
      })
      graphInstance.use(new Export())

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
      const isBroker = node.type === 'HOST'
      const isKafka = node.type === 'KAFKA'
      graphInstance!.addNode({
        id: node.id,
        x: node.x - NODE_WIDTH / 2,
        y: node.y - NODE_HEIGHT / 2,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
        attrs: {
          body: {
            stroke: isKafka ? '#1d4ed8' : isBroker ? '#64748b' : '#1f4f46',
            strokeWidth: 1.5,
            fill: isBroker ? '#f8fafc' : '#ffffff',
            rx: 10,
            ry: 10,
          },
          label: {
            text: `${node.label}\n${endpointTypeLabel(node.type)}`,
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
        markup: [
          { tagName: 'path', selector: 'wrap' },
          { tagName: 'path', selector: 'line' },
        ],
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
          wrap: {
            fill: 'none',
            connection: true,
            stroke: 'transparent',
            strokeWidth: 16,
            cursor: 'pointer',
          },
          line: {
            connection: true,
            stroke,
            strokeWidth: edge.primary ? 2.5 : 1.5,
            strokeDasharray: edge.primary ? undefined : '6 4',
            targetMarker: {
              name: 'block',
              width: 10,
              height: 8,
            },
            cursor: 'pointer',
          },
        },
        data: edge,
        zIndex: 1,
      })
    })

    ;(props.graph.relations ?? []).forEach((rel) => {
      graphInstance!.addEdge({
        id: rel.id,
        source: rel.source,
        target: rel.target,
        labels: [
          {
            attrs: {
              label: {
                text: rel.label || rel.type,
                fill: '#64748b',
                fontSize: 10,
                fontFamily: 'IBM Plex Sans, sans-serif',
                pointerEvents: 'none',
              },
              body: {
                fill: '#f8fafc',
                stroke: '#cbd5e1',
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
            stroke: '#94a3b8',
            strokeWidth: 1.2,
            strokeDasharray: '4 4',
            targetMarker: {
              name: 'classic',
              width: 8,
              height: 6,
            },
          },
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

defineExpose({ zoomToFit, exportPng, render })

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
}

.canvas {
  width: 100%;
  height: 100%;
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
