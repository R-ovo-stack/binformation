import { AntVDagreLayout } from '@antv/layout'
import type { AssetGraph, GraphEdge, GraphNode, GraphRelation } from '@/types/graph'

const NODE_WIDTH = 188
const NODE_HEIGHT = 64
const NODE_GAP_X = 48
const NODE_GAP_Y = 36

export interface PositionedNode extends GraphNode {
  x: number
  y: number
}

/**
 * 布局用边：避免「业务流向」与「经程序处理」双线并行导致重叠。
 * - 有 VIA_EXECUTOR 时，用 源→程序→目标 做布局，不把同流向的直连边再计入布局
 * - 始终纳入 CONTAINS / BROKER_OF / RUNS_ON
 */
function buildLayoutEdges(graph: AssetGraph): Array<{ id: string; source: string; target: string }> {
  const relations = graph.relations ?? []
  const viaRels = relations.filter((r) => r.type === 'VIA_EXECUTOR')
  const otherRels = relations.filter((r) => r.type !== 'VIA_EXECUTOR')

  const flowIdsSkipDirect = new Set<string>()
  graph.edges.forEach((edge) => {
    const hasVia = viaRels.some(
      (r) =>
        (r.source === edge.source && r.target.startsWith('exec-')) ||
        (r.target === edge.target && r.source.startsWith('exec-')),
    )
    if (hasVia) flowIdsSkipDirect.add(edge.id)
  })

  const edges: Array<{ id: string; source: string; target: string }> = []

  graph.edges.forEach((edge) => {
    if (flowIdsSkipDirect.has(edge.id)) return
    edges.push({ id: edge.id, source: edge.source, target: edge.target })
  })

  viaRels.forEach((rel) => {
    edges.push({ id: rel.id, source: rel.source, target: rel.target })
  })

  otherRels.forEach((rel) => {
    edges.push({ id: rel.id, source: rel.source, target: rel.target })
  })

  return edges
}

function overlaps(
  a: PositionedNode,
  b: PositionedNode,
  gapX = NODE_GAP_X,
  gapY = NODE_GAP_Y,
): boolean {
  return (
    Math.abs(a.x - b.x) < NODE_WIDTH + gapX &&
    Math.abs(a.y - b.y) < NODE_HEIGHT + gapY
  )
}

/** 简单碰撞推开，减少节点叠在一起 */
function resolveOverlaps(nodes: PositionedNode[]): PositionedNode[] {
  const result = nodes.map((n) => ({ ...n }))
  const minDx = NODE_WIDTH + NODE_GAP_X
  const minDy = NODE_HEIGHT + NODE_GAP_Y

  for (let iter = 0; iter < 12; iter++) {
    let moved = false
    for (let i = 0; i < result.length; i++) {
      for (let j = i + 1; j < result.length; j++) {
        const a = result[i]
        const b = result[j]
        if (!overlaps(a, b)) continue

        const dx = b.x - a.x || 0.01
        const dy = b.y - a.y || 0.01
        const preferVertical =
          a.groupId && a.groupId === b.groupId && Math.abs(dx) < minDx * 0.6

        if (preferVertical || Math.abs(dy) * minDx >= Math.abs(dx) * minDy) {
          const push = (minDy - Math.abs(dy)) / 2 + 4
          const sign = dy >= 0 ? 1 : -1
          a.y -= sign * push
          b.y += sign * push
        } else {
          const push = (minDx - Math.abs(dx)) / 2 + 4
          const sign = dx >= 0 ? 1 : -1
          a.x -= sign * push
          b.x += sign * push
        }
        moved = true
      }
    }
    if (!moved) break
  }

  // 按安全区分带，避免跨区节点挤在同一列
  const byZone = new Map<string, PositionedNode[]>()
  result.forEach((n) => {
    const key = n.groupId || '_none'
    if (!byZone.has(key)) byZone.set(key, [])
    byZone.get(key)!.push(n)
  })

  if (byZone.size > 1) {
    const zones = [...byZone.entries()]
    let cursorX = 0
    zones.forEach(([, list]) => {
      const minX = Math.min(...list.map((n) => n.x - NODE_WIDTH / 2))
      const maxX = Math.max(...list.map((n) => n.x + NODE_WIDTH / 2))
      const width = maxX - minX
      const shift = cursorX - minX
      list.forEach((n) => {
        n.x += shift
      })
      cursorX += width + 120
    })
  }

  return result
}

export async function layoutGraph(graph: AssetGraph): Promise<PositionedNode[]> {
  const hasSavedLayout = graph.nodes.some(
    (n) => n.layoutX != null && n.layoutY != null,
  )

  if (hasSavedLayout) {
    return resolveOverlaps(
      graph.nodes.map((node, index) => ({
        ...node,
        x: node.layoutX ?? 80 + (index % 4) * 240,
        y: node.layoutY ?? 80 + Math.floor(index / 4) * 140,
      })),
    )
  }

  if (graph.nodes.length === 0) {
    return []
  }

  const layoutEdges = buildLayoutEdges(graph)

  const layout = new AntVDagreLayout({
    rankdir: 'LR',
    align: 'UL',
    nodesep: 72,
    ranksep: 160,
    ranker: 'tight-tree',
    nodeSize: [NODE_WIDTH, NODE_HEIGHT],
    controlPoints: false,
    begin: [80, 80],
  })

  await layout.execute({
    nodes: graph.nodes.map((node) => ({ id: node.id })),
    edges: layoutEdges,
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

  if (positioned.length === 0) {
    return resolveOverlaps(
      graph.nodes.map((node, index) => ({
        ...node,
        x: 100 + (index % 4) * 240,
        y: 100 + Math.floor(index / 4) * 140,
      })),
    )
  }

  return resolveOverlaps(positioned)
}

/** 渲染时隐藏 VIA_EXECUTOR，避免与业务流向叠线；程序节点仍通过 RUNS_ON 体现部署关系 */
export function visibleRelations(relations: GraphRelation[] | undefined): GraphRelation[] {
  return (relations ?? []).filter((r) => r.type !== 'VIA_EXECUTOR')
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
