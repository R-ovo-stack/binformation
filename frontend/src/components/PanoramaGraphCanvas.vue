<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Graph } from '@antv/x6'
import type { PanoramaEdge, PanoramaGraph } from '@/types/panorama'
import { dataTypeLabel, panoramaEdgeStroke } from '@/types/panorama'
import { layoutPanoramaGraph, panoramaNodeId } from '@/utils/panoramaLayout'

const props = defineProps<{
  graph: PanoramaGraph | null
  selectedAssetId?: number | null
}>()

const emit = defineEmits<{
  selectAsset: [assetId: number | null]
  selectEdge: [edge: PanoramaEdge | null]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
let graphInstance: Graph | null = null
let renderSeq = 0

function nodeFill(dataType: string): string {
  return dataType === 'KAFKA_MESSAGE' ? '#eef2ff' : '#ecfdf5'
}

function nodeStroke(dataType: string): string {
  return dataType === 'KAFKA_MESSAGE' ? '#4338ca' : '#0f766e'
}

function destroyGraph() {
  if (graphInstance) {
    try {
      graphInstance.off()
      graphInstance.dispose()
    } catch {
      // ignore
    }
    graphInstance = null
  }
  if (containerRef.value) containerRef.value.innerHTML = ''
}

function render() {
  const g = props.graph
  if (!containerRef.value || !g) return
  const seq = ++renderSeq

  destroyGraph()

  graphInstance = new Graph({
    container: containerRef.value,
    autoResize: true,
    panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheelDown'] },
    mousewheel: {
      enabled: true,
      modifiers: null,
      factor: 1.12,
      minScale: 0.15,
      maxScale: 2.5,
      zoomAtMousePosition: true,
    },
    background: { color: '#f4f7f5' },
    grid: { visible: true, type: 'dot', args: { color: '#d7e0db', thickness: 1 } },
    connecting: { router: { name: 'orth' }, connector: { name: 'rounded', args: { radius: 8 } } },
  })

  graphInstance.on('node:click', ({ node }) => {
    const id = Number(String(node.id).replace(/^asset-/, ''))
    emit('selectAsset', Number.isFinite(id) ? id : null)
    emit('selectEdge', null)
  })

  graphInstance.on('edge:click', ({ edge }) => {
    const data = edge.getData() as PanoramaEdge | undefined
    emit('selectEdge', data ?? null)
    emit('selectAsset', null)
  })

  graphInstance.on('blank:click', () => {
    emit('selectAsset', null)
    emit('selectEdge', null)
  })

  const positioned = layoutPanoramaGraph(g)
  if (seq !== renderSeq || !graphInstance) return

  positioned.forEach((node) => {
    const selected = props.selectedAssetId === node.assetId
    const badges: string[] = []
    if (node.derivationInCount) badges.push(`输入×${node.derivationInCount}`)
    if (node.derivationOutCount) badges.push(`派生×${node.derivationOutCount}`)
    const sub = [
      node.code,
      dataTypeLabel(node.dataType),
      node.primaryFlowCount ? `${node.primaryFlowCount} 条主流向` : '无主流向',
      badges.length ? badges.join(' ') : '',
    ]
      .filter(Boolean)
      .join('\n')

    graphInstance!.addNode({
      id: panoramaNodeId(node.assetId),
      x: node.x - node.width / 2,
      y: node.y - node.height / 2,
      width: node.width,
      height: node.height,
      shape: 'rect',
      zIndex: selected ? 10 : 2,
      attrs: {
        body: {
          fill: nodeFill(node.dataType),
          stroke: selected ? '#0f172a' : nodeStroke(node.dataType),
          strokeWidth: selected ? 2.5 : 1.6,
          rx: 12,
          ry: 12,
        },
        label: {
          text: `${node.name}\n${sub}`,
          fill: '#0f172a',
          fontSize: 12,
          fontWeight: 600,
          fontFamily: 'IBM Plex Sans, sans-serif',
          textWrap: { width: node.width - 16, height: node.height - 12, ellipsis: true },
        },
      },
      data: node,
    })
  })

  g.edges.forEach((edge) => {
    const stroke = panoramaEdgeStroke(edge.type)
    const isDerive = edge.type === 'DERIVE'
    graphInstance!.addEdge({
      id: edge.id,
      source: panoramaNodeId(edge.sourceAssetId),
      target: panoramaNodeId(edge.targetAssetId),
      labels: [
        {
          attrs: {
            label: {
              text: isDerive ? '派生' : '落点衔接',
              fill: '#0f172a',
              fontSize: 10,
              fontFamily: 'IBM Plex Sans, sans-serif',
            },
            body: {
              fill: isDerive ? '#ecfdf5' : '#eff6ff',
              stroke,
              strokeWidth: 1,
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
          strokeWidth: isDerive ? 2.2 : 1.6,
          strokeDasharray: isDerive ? undefined : '6 4',
          targetMarker: { name: 'block', width: 10, height: 8, fill: stroke, stroke },
        },
      },
      router: { name: 'orth', args: { padding: 16 } },
      connector: { name: 'rounded', args: { radius: 8 } },
      data: edge,
      zIndex: 1,
    })
  })

  if (seq !== renderSeq || !graphInstance) return
  graphInstance.zoomToFit({ padding: 48, maxScale: 1.15 })
}

function zoomToFit() {
  graphInstance?.zoomToFit({ padding: 48, maxScale: 1.15 })
}

defineExpose({ zoomToFit, render })

watch(
  () => [props.graph, props.selectedAssetId] as const,
  () => render(),
  { deep: true },
)

onMounted(() => render())
onBeforeUnmount(() => destroyGraph())
</script>

<template>
  <div class="panorama-wrap">
    <div ref="containerRef" class="canvas" />
  </div>
</template>

<style scoped>
.panorama-wrap {
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
</style>
