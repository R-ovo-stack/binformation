import { DagreLayout } from '@antv/layout'
import type { AssetGraph, GraphEdge, GraphNode } from '@/types/graph'

const NODE_WIDTH = 180
const NODE_HEIGHT = 56

export interface PositionedNode extends GraphNode {
  x: number
  y: number
}

export async function layoutGraph(graph: AssetGraph): Promise<PositionedNode[]> {
  const hasSavedLayout = graph.nodes.some(
    (n) => n.layoutX != null && n.layoutY != null,
  )

  if (hasSavedLayout) {
    return graph.nodes.map((node, index) => ({
      ...node,
      x: node.layoutX ?? 80 + (index % 4) * 220,
      y: node.layoutY ?? 80 + Math.floor(index / 4) * 120,
    }))
  }

  if (graph.nodes.length === 0) {
    return []
  }

  const layout = new DagreLayout({
    rankdir: 'LR',
    nodesep: 48,
    ranksep: 90,
    nodeSize: [NODE_WIDTH, NODE_HEIGHT],
  })

  await layout.execute({
    nodes: graph.nodes.map((node) => ({ id: node.id })),
    edges: [
      ...graph.edges.map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
      })),
      ...(graph.relations ?? []).map((rel) => ({
        id: rel.id,
        source: rel.source,
        target: rel.target,
      })),
    ],
  })

  const positioned: PositionedNode[] = []
  layout.forEachNode((layoutNode) => {
    const source = graph.nodes.find((n) => n.id === String(layoutNode.id))
    if (!source) return
    positioned.push({
      ...source,
      x: layoutNode.x ?? 0,
      y: layoutNode.y ?? 0,
    })
  })

  // 兜底：布局未返回坐标时按网格排布
  if (positioned.length === 0) {
    return graph.nodes.map((node, index) => ({
      ...node,
      x: 80 + (index % 4) * 220,
      y: 80 + Math.floor(index / 4) * 120,
    }))
  }

  return positioned
}

export function purposeLabel(purpose: string): string {
  const map: Record<string, string> = {
    INGEST: '接入',
    SHARE: '共享',
    SYNC: '同步',
    FORWARD: '转发',
    AUX: '辅助',
  }
  return map[purpose] ?? purpose
}

export function methodLabel(method: string): string {
  const map: Record<string, string> = {
    DIRECT_PUSH: '直推',
    CROSS_ZONE_PUSH: '跨区隔离推送',
    KAFKA_SUBSCRIBE_FORWARD: '订阅转发',
    NOTIFY_THEN_PULL: '通知+拉取',
    NOTIFY_THEN_SHARED_READ: '通知+共享读取',
    OTHER: '其他',
  }
  return map[method] ?? method
}

export function endpointTypeLabel(type: string): string {
  const map: Record<string, string> = {
    SECURITY_ZONE: '安全区',
    SYSTEM: '系统',
    KAFKA: 'Kafka',
    ROCKETMQ: 'RocketMQ',
    OBJECT_STORAGE: '对象存储',
    HOST: '主机',
    KAFKA_TOPIC: 'Kafka主题',
    ROCKETMQ_TOPIC: 'RocketMQ主题',
    OBJECT_BUCKET: '对象桶',
    OBJECT_PREFIX: '对象目录',
    DIRECTORY: '目录',
    HTTP_API: 'HTTP接口',
    PROGRAM: '程序',
    SCRIPT: '脚本',
  }
  return map[type] ?? type
}

export function edgeStroke(edge: GraphEdge): string {
  if (!edge.primary) return '#94a3b8'
  switch (edge.purpose) {
    case 'INGEST':
      return '#0f766e'
    case 'SHARE':
      return '#b45309'
    case 'SYNC':
      return '#1d4ed8'
    case 'FORWARD':
      return '#6d28d9'
    default:
      return '#334155'
  }
}

export { NODE_WIDTH, NODE_HEIGHT }
