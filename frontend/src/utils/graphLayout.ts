import type { AssetGraph, GraphEdge, GraphNode, GraphRelation } from '@/types/graph'

export type LayoutMode = 'compact' | 'full'

const NODE_WIDTH = 188
const NODE_HEIGHT = 64
const COL_GAP = 280
const LANE_GAP = 220
const ROW_IN_LANE = 120
const SAT_DY = 118

export interface PositionedNode extends GraphNode {
  x: number
  y: number
}

/** 画布上实际绘制的边（把流向展开成 源→程序→目标） */
export interface DisplayEdge {
  id: string
  source: string
  target: string
  purpose: string
  primary: boolean
  label: string
  flowEdge: GraphEdge
}

function nodeById(graph: AssetGraph): Map<string, GraphNode> {
  return new Map(graph.nodes.map((n) => [n.id, n]))
}

function executorIdsForEdge(edge: GraphEdge): string[] {
  const path = [...(edge.paths || [])]
    .filter((p) => p.enabled !== false)
    .sort((a, b) => a.sortOrder - b.sortOrder)[0]
  if (!path?.steps?.length) return []
  const ordered = [...path.steps].sort((a, b) => a.seq - b.seq)
  const ids: string[] = []
  for (const step of ordered) {
    const id = `exec-${step.executorId}`
    if (!ids.includes(id)) ids.push(id)
  }
  return ids
}

function hostIdsForEdge(edge: GraphEdge): string[] {
  const path = [...(edge.paths || [])]
    .filter((p) => p.enabled !== false)
    .sort((a, b) => a.sortOrder - b.sortOrder)[0]
  if (!path?.steps?.length) return []
  const hosts: string[] = []
  for (const step of [...path.steps].sort((a, b) => a.seq - b.seq)) {
    if (step.hostId == null) continue
    const id = `ep-${step.hostId}`
    if (!hosts.includes(id)) hosts.push(id)
  }
  return hosts
}

/** 简洁模式：主链路落点 + 程序 + 部署主机；完整模式保留全部 */
export function filterGraphForMode(graph: AssetGraph, mode: LayoutMode): AssetGraph {
  if (mode === 'full') return graph

  const keep = new Set<string>()
  const primaryEdges = graph.edges.filter((e) => e.primary)

  primaryEdges.forEach((edge) => {
    keep.add(edge.source)
    keep.add(edge.target)
    executorIdsForEdge(edge).forEach((id) => keep.add(id))
    hostIdsForEdge(edge).forEach((id) => keep.add(id))
  })

  ;(graph.relations || []).forEach((r) => {
    if (r.type === 'RUNS_ON' && keep.has(r.source)) {
      keep.add(r.target)
    }
  })

  const nodes = graph.nodes.filter((n) => keep.has(n.id))
  const nodeIds = new Set(nodes.map((n) => n.id))
  const edges = primaryEdges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target))
  const relations = (graph.relations || []).filter(
    (r) =>
      r.type === 'RUNS_ON' &&
      nodeIds.has(r.source) &&
      nodeIds.has(r.target),
  )
  const groupIds = new Set(nodes.map((n) => n.groupId).filter(Boolean) as string[])
  const groups = graph.groups.filter((g) => groupIds.has(g.id))

  return { ...graph, nodes, edges, relations, groups }
}

/**
 * 把每条流向展开为：源 → 程序1 → … → 程序N → 目标
 * 点击任一段仍回到原 Flow，便于看路径/步骤详情。
 */
export function expandDisplayEdges(graph: AssetGraph): DisplayEdge[] {
  const result: DisplayEdge[] = []
  const nodeIds = new Set(graph.nodes.map((n) => n.id))

  graph.edges.forEach((edge) => {
    const chain = [edge.source, ...executorIdsForEdge(edge), edge.target].filter((id) =>
      nodeIds.has(id),
    )
    // 去重相邻重复（同程序多 step）
    const compact: string[] = []
    chain.forEach((id) => {
      if (compact[compact.length - 1] !== id) compact.push(id)
    })

    if (compact.length < 2) {
      if (nodeIds.has(edge.source) && nodeIds.has(edge.target)) {
        result.push({
          id: edge.id,
          source: edge.source,
          target: edge.target,
          purpose: edge.purpose,
          primary: edge.primary,
          label: purposeLabel(edge.purpose),
          flowEdge: edge,
        })
      }
      return
    }

    for (let i = 0; i < compact.length - 1; i++) {
      const isFirst = i === 0
      const isLast = i === compact.length - 2
      let label = purposeLabel(edge.purpose)
      if (!isFirst && !isLast) label = '经程序'
      else if (!isFirst && isLast) label = '写出'
      else if (isFirst && !isLast) label = purposeLabel(edge.purpose)

      result.push({
        id: `${edge.id}#${i}`,
        source: compact[i],
        target: compact[i + 1],
        purpose: edge.purpose,
        primary: edge.primary,
        label,
        flowEdge: edge,
      })
    }
  })

  return result
}

function inferZoneOrder(graph: AssetGraph): string[] {
  const groups = graph.groups.map((g) => g.id)
  if (groups.length <= 1) return groups

  const score = new Map<string, number>()
  groups.forEach((g) => score.set(g, 0))
  const map = nodeById(graph)

  expandDisplayEdges(graph).forEach((e) => {
    const s = map.get(e.source)
    const t = map.get(e.target)
    if (!s?.groupId || !t?.groupId || s.groupId === t.groupId) return
    score.set(t.groupId, (score.get(t.groupId) || 0) + 3)
    score.set(s.groupId, (score.get(s.groupId) || 0) - 1)
  })

  return [...groups].sort((a, b) => (score.get(a) || 0) - (score.get(b) || 0))
}

/** 最长路径分层：从左到右 */
function assignLayers(
  nodeIds: string[],
  edges: Array<{ source: string; target: string }>,
): Map<string, number> {
  const preds = new Map<string, string[]>()
  const succs = new Map<string, string[]>()
  nodeIds.forEach((id) => {
    preds.set(id, [])
    succs.set(id, [])
  })
  edges.forEach((e) => {
    if (!preds.has(e.source) || !preds.has(e.target)) return
    if (e.source === e.target) return
    succs.get(e.source)!.push(e.target)
    preds.get(e.target)!.push(e.source)
  })

  const layer = new Map<string, number>()
  const indeg = new Map<string, number>()
  nodeIds.forEach((id) => indeg.set(id, preds.get(id)!.length))

  const queue = nodeIds.filter((id) => (indeg.get(id) || 0) === 0)
  queue.forEach((id) => layer.set(id, 0))

  const visited = new Set<string>()
  while (queue.length) {
    const cur = queue.shift()!
    if (visited.has(cur)) continue
    visited.add(cur)
    const base = layer.get(cur) || 0
    for (const next of succs.get(cur) || []) {
      const candidate = base + 1
      layer.set(next, Math.max(layer.get(next) || 0, candidate))
      indeg.set(next, (indeg.get(next) || 1) - 1)
      if ((indeg.get(next) || 0) <= 0) queue.push(next)
    }
  }

  // 环或未触达：按已有前驱再推一轮
  nodeIds.forEach((id) => {
    if (layer.has(id)) return
    const parentLayers = (preds.get(id) || [])
      .map((p) => layer.get(p))
      .filter((v): v is number => v != null)
    layer.set(id, parentLayers.length ? Math.max(...parentLayers) + 1 : 0)
  })

  return layer
}

function barycenter(
  id: string,
  edges: Array<{ source: string; target: string }>,
  posY: Map<string, number>,
): number {
  const neighbors: number[] = []
  edges.forEach((e) => {
    if (e.target === id && posY.has(e.source)) neighbors.push(posY.get(e.source)!)
    if (e.source === id && posY.has(e.target)) neighbors.push(posY.get(e.target)!)
  })
  if (!neighbors.length) return Number.POSITIVE_INFINITY
  return neighbors.reduce((a, b) => a + b, 0) / neighbors.length
}

/**
 * 领域感知布局：
 * 1) 流向按步骤展开后拓扑分层（左→右）
 * 2) 安全区做水平泳道（上→下）
 * 3) 层内用邻接重心减少交叉
 * 4) Kafka/Broker/部署主机作卫星就近挂载
 */
export function layoutGraphSmart(graph: AssetGraph, mode: LayoutMode = 'compact'): PositionedNode[] {
  const view = filterGraphForMode(graph, mode)
  if (view.nodes.length === 0) return []

  if (view.nodes.some((n) => n.layoutX != null && n.layoutY != null)) {
    return resolveOverlaps(
      view.nodes.map((n, i) => ({
        ...n,
        x: n.layoutX ?? 100 + (i % 4) * COL_GAP,
        y: n.layoutY ?? 100 + Math.floor(i / 4) * ROW_IN_LANE,
      })),
    )
  }

  const map = nodeById(view)
  const displayEdges = expandDisplayEdges(view)
  const mainIds = new Set<string>()
  displayEdges.forEach((e) => {
    mainIds.add(e.source)
    mainIds.add(e.target)
  })

  // 主链节点：落点 + 程序；主机/Kafka/Broker 作为卫星
  const isSatellite = (n: GraphNode) => {
    if (n.kind === 'EXECUTOR') return false
    if (n.type === 'HOST') return true
    if (mode === 'full' && (n.type === 'KAFKA' || n.type === 'ROCKETMQ')) return true
    return false
  }

  const spineIds = [...mainIds].filter((id) => {
    const n = map.get(id)
    return n && !isSatellite(n)
  })

  const dagEdges = displayEdges
    .filter((e) => spineIds.includes(e.source) && spineIds.includes(e.target))
    .map((e) => ({ source: e.source, target: e.target }))

  const layers = assignLayers(spineIds, dagEdges)
  const zoneOrder = inferZoneOrder(view)
  const zoneIndex = (n: GraphNode) => {
    if (!n.groupId) return zoneOrder.length
    const idx = zoneOrder.indexOf(n.groupId)
    return idx >= 0 ? idx : zoneOrder.length
  }

  const byLayer = new Map<number, string[]>()
  spineIds.forEach((id) => {
    const l = layers.get(id) || 0
    if (!byLayer.has(l)) byLayer.set(l, [])
    byLayer.get(l)!.push(id)
  })

  const positions = new Map<string, { x: number; y: number }>()
  const sortedLayers = [...byLayer.keys()].sort((a, b) => a - b)
  const roughY = new Map<string, number>()

  // 先按泳道给粗 Y，再按层精排
  spineIds.forEach((id) => {
    const n = map.get(id)!
    roughY.set(id, 160 + zoneIndex(n) * LANE_GAP)
  })

  sortedLayers.forEach((layerNo) => {
    const ids = byLayer.get(layerNo)!
    ids.sort((a, b) => {
      const na = map.get(a)!
      const nb = map.get(b)!
      const za = zoneIndex(na)
      const zb = zoneIndex(nb)
      if (za !== zb) return za - zb
      const ba = barycenter(a, dagEdges, roughY)
      const bb = barycenter(b, dagEdges, roughY)
      if (ba !== bb) return ba - bb
      const wa = na.kind === 'EXECUTOR' ? 0 : 1
      const wb = nb.kind === 'EXECUTOR' ? 0 : 1
      if (wa !== wb) return wa - wb
      return (na.label || '').localeCompare(nb.label || '', 'zh')
    })

    // 同层按区分组，区内再错开
    const usedInLane = new Map<number, number>()
    const x = 140 + layerNo * COL_GAP
    ids.forEach((id) => {
      const node = map.get(id)!
      const lane = zoneIndex(node)
      const slot = usedInLane.get(lane) || 0
      usedInLane.set(lane, slot + 1)
      const y = 160 + lane * LANE_GAP + slot * ROW_IN_LANE
      positions.set(id, { x, y })
      roughY.set(id, y)
    })
  })

  const allRelations = mode === 'full' ? graph.relations || [] : view.relations || []

  if (mode === 'full') {
    // Kafka 在 Topic 上方
    allRelations
      .filter((r) => r.type === 'CONTAINS')
      .forEach((r) => {
        const topicPos = positions.get(r.target)
        if (!topicPos || positions.has(r.source)) return
        positions.set(r.source, {
          x: topicPos.x,
          y: topicPos.y - SAT_DY,
        })
      })

    const brokersByKafka = new Map<string, string[]>()
    allRelations
      .filter((r) => r.type === 'BROKER_OF')
      .forEach((r) => {
        if (!brokersByKafka.has(r.source)) brokersByKafka.set(r.source, [])
        brokersByKafka.get(r.source)!.push(r.target)
      })

    brokersByKafka.forEach((brokers, kafkaId) => {
      const kp = positions.get(kafkaId)
      if (!kp) return
      brokers.forEach((bid, i) => {
        if (positions.has(bid)) return
        const offset = (i - (brokers.length - 1) / 2) * (NODE_WIDTH + 24)
        positions.set(bid, {
          x: kp.x + offset,
          y: kp.y + SAT_DY,
        })
      })
    })
  }

  // 部署主机挂在程序下方；多程序同主机只放一次（靠第一个程序）
  allRelations
    .filter((r) => r.type === 'RUNS_ON')
    .forEach((r) => {
      const ep = positions.get(r.source)
      if (!ep) return
      if (positions.has(r.target)) {
        // 已有位置时，若仍重叠则略偏移
        return
      }
      positions.set(r.target, {
        x: ep.x,
        y: ep.y + SAT_DY,
      })
    })

  let orphanIndex = 0
  view.nodes.forEach((n) => {
    if (positions.has(n.id)) return
    positions.set(n.id, {
      x: 140 + (sortedLayers.length + Math.floor(orphanIndex / 3)) * COL_GAP,
      y: 160 + (orphanIndex % 3) * ROW_IN_LANE,
    })
    orphanIndex++
  })

  const positioned: PositionedNode[] = view.nodes.map((n) => {
    const p = positions.get(n.id)!
    return { ...n, x: p.x, y: p.y }
  })

  return resolveOverlaps(positioned)
}

function overlaps(a: PositionedNode, b: PositionedNode): boolean {
  return (
    Math.abs(a.x - b.x) < NODE_WIDTH + 40 &&
    Math.abs(a.y - b.y) < NODE_HEIGHT + 32
  )
}

function resolveOverlaps(nodes: PositionedNode[]): PositionedNode[] {
  const result = nodes.map((n) => ({ ...n }))
  for (let iter = 0; iter < 20; iter++) {
    let moved = false
    for (let i = 0; i < result.length; i++) {
      for (let j = i + 1; j < result.length; j++) {
        const a = result[i]
        const b = result[j]
        if (!overlaps(a, b)) continue
        const dy = (b.y - a.y) || 1
        const sign = dy >= 0 ? 1 : -1
        a.y -= sign * 22
        b.y += sign * 22
        // 同层也拉开一点 X
        if (Math.abs(a.x - b.x) < 8) {
          a.x -= 12
          b.x += 12
        }
        moved = true
      }
    }
    if (!moved) break
  }
  return result
}

/** 关系边：简洁只画部署；完整画拓扑，不画 VIA（已并入主链路展开） */
export function visibleRelations(
  relations: GraphRelation[] | undefined,
  mode: LayoutMode,
): GraphRelation[] {
  const list = relations ?? []
  if (mode === 'compact') {
    return list.filter((r) => r.type === 'RUNS_ON')
  }
  return list.filter((r) => r.type !== 'VIA_EXECUTOR')
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

export function edgeStroke(edge: Pick<GraphEdge, 'primary' | 'purpose'>): string {
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

export async function layoutGraph(
  graph: AssetGraph,
  mode: LayoutMode = 'compact',
): Promise<PositionedNode[]> {
  return layoutGraphSmart(graph, mode)
}
