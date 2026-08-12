import type { AssetGraph, GraphEdge, GraphNode, GraphRelation } from '@/types/graph'

export type LayoutMode = 'compact' | 'full'

const NODE_WIDTH = 188
const NODE_HEIGHT = 64
const COL_GAP = 300
const LANE_GAP = 250
const ROW_IN_LANE = 140
const SAT_DY = 118

export interface PositionedNode extends GraphNode {
  /** 节点中心 X（叶子）或容器中心 X */
  x: number
  y: number
  width?: number
  height?: number
  /** CONTAINS / 集群卡片父节点 id */
  parentNodeId?: string | null
  /** Kafka 等集群卡片 */
  isContainer?: boolean
  /** 卡片内角色：主题 / Broker 芯片 */
  nestRole?: 'topic' | 'broker' | null
}

/** Kafka 集群卡片几何：标题栏 + 主题区 + 可选 Broker 底栏 */
const CLUSTER_MIN_W = 248
const CLUSTER_HEADER = 36
const CLUSTER_PAD = 14
const TOPIC_INNER_W = 212
const TOPIC_INNER_H = 56
const BROKER_CHIP_W = 92
const BROKER_CHIP_H = 34
const INNER_GAP = 10

function clusterMetrics(topicCount: number, brokerCount: number) {
  const topicsW =
    Math.max(1, topicCount) * TOPIC_INNER_W + Math.max(0, topicCount - 1) * INNER_GAP
  const brokersW =
    brokerCount > 0
      ? brokerCount * BROKER_CHIP_W + Math.max(0, brokerCount - 1) * INNER_GAP
      : 0
  const width = Math.max(CLUSTER_MIN_W, topicsW + CLUSTER_PAD * 2, brokersW + CLUSTER_PAD * 2)
  let height = CLUSTER_HEADER + CLUSTER_PAD + TOPIC_INNER_H + CLUSTER_PAD
  if (brokerCount > 0) {
    height += BROKER_CHIP_H + CLUSTER_PAD
  }
  return { width, height }
}

/** 画布上实际绘制的边（把流向展开成 源→程序→目标） */
export interface DisplayEdge {
  id: string
  source: string
  target: string
  purpose: string
  primary: boolean
  upstream?: boolean
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
  // 主流向 + 前置/桥接边（upstream）均保留在简洁模式
  const primaryEdges = graph.edges.filter((e) => e.primary || e.upstream)

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
          upstream: Boolean(edge.upstream),
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
        upstream: Boolean(edge.upstream),
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
 * 4) 完整模式：CONTAINS 画成大框套小框；Broker / 部署主机作卫星
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
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
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

  // 主链：落点+程序；HOST /（完整模式）Kafka|主机容器先不当脊柱点
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
  const parentOf = new Map<string, string>()
  const childrenOf = new Map<string, string[]>()
  const containerMeta = new Map<string, { width: number; height: number; x: number; y: number }>()
  const nestRole = new Map<string, 'topic' | 'broker'>()
  const leafSize = new Map<string, { width: number; height: number }>()

  if (mode === 'full') {
    allRelations
      .filter((r) => r.type === 'CONTAINS')
      .forEach((r) => {
        if (!positions.has(r.target) && !map.has(r.target)) return
        parentOf.set(r.target, r.source)
        const childNode = map.get(r.target)
        const role =
          childNode?.type === 'KAFKA_TOPIC' || childNode?.type === 'ROCKETMQ_TOPIC'
            ? 'topic'
            : childNode?.type === 'DIRECTORY'
              ? 'topic' // 目录也用内容区主块样式
              : 'topic'
        nestRole.set(r.target, role)
        if (!childrenOf.has(r.source)) childrenOf.set(r.source, [])
        if (!childrenOf.get(r.source)!.includes(r.target)) {
          childrenOf.get(r.source)!.push(r.target)
        }
      })

    const brokersByKafka = new Map<string, string[]>()
    allRelations
      .filter((r) => r.type === 'BROKER_OF')
      .forEach((r) => {
        if (!brokersByKafka.has(r.source)) brokersByKafka.set(r.source, [])
        brokersByKafka.get(r.source)!.push(r.target)
      })

    // 先保证每个被包含主题有坐标（若仅父在图中）
    childrenOf.forEach((topicIds) => {
      topicIds.forEach((tid, i) => {
        if (positions.has(tid)) return
        const sibling = topicIds.map((id) => positions.get(id)).find(Boolean)
        positions.set(tid, {
          x: (sibling?.x ?? 140) + i * (TOPIC_INNER_W + INNER_GAP),
          y: sibling?.y ?? 160,
        })
      })
    })

    childrenOf.forEach((topicIds, kafkaId) => {
      const brokers = (brokersByKafka.get(kafkaId) || []).filter((id) => map.has(id))
      const metrics = clusterMetrics(topicIds.length, brokers.length)

      // 以主题脊柱位置为锚：卡片中心对齐主题列，主题落在标题下的内容区
      const anchors = topicIds.map((id) => positions.get(id)!).filter(Boolean)
      if (!anchors.length) return
      const anchorX = anchors.reduce((s, p) => s + p.x, 0) / anchors.length
      const anchorY = anchors.reduce((s, p) => s + p.y, 0) / anchors.length

      // 内容区中心（主题行）相对卡片中心的偏移
      const contentCenterOffsetY =
        -metrics.height / 2 + CLUSTER_HEADER + CLUSTER_PAD + TOPIC_INNER_H / 2
      const cardCx = anchorX
      const cardCy = anchorY - contentCenterOffsetY

      positions.set(kafkaId, { x: cardCx, y: cardCy })
      containerMeta.set(kafkaId, {
        width: metrics.width,
        height: metrics.height,
        x: cardCx,
        y: cardCy,
      })

      // 主题横排，整体水平居中于卡片
      const topicsSpan =
        topicIds.length * TOPIC_INNER_W + Math.max(0, topicIds.length - 1) * INNER_GAP
      const topicLeft = cardCx - topicsSpan / 2
      topicIds.forEach((tid, i) => {
        const tx = topicLeft + TOPIC_INNER_W / 2 + i * (TOPIC_INNER_W + INNER_GAP)
        const ty = cardCy + contentCenterOffsetY
        positions.set(tid, { x: tx, y: ty })
        parentOf.set(tid, kafkaId)
        nestRole.set(tid, 'topic')
        leafSize.set(tid, { width: TOPIC_INNER_W, height: TOPIC_INNER_H })
      })

      // Broker 芯片收进卡片底栏（视觉归属集群，不再甩在外面）
      if (brokers.length) {
        const brokersSpan =
          brokers.length * BROKER_CHIP_W + Math.max(0, brokers.length - 1) * INNER_GAP
        const brokerLeft = cardCx - brokersSpan / 2
        const brokerCy =
          cardCy + metrics.height / 2 - CLUSTER_PAD - BROKER_CHIP_H / 2
        brokers.forEach((bid, i) => {
          positions.set(bid, {
            x: brokerLeft + BROKER_CHIP_W / 2 + i * (BROKER_CHIP_W + INNER_GAP),
            y: brokerCy,
          })
          parentOf.set(bid, kafkaId)
          nestRole.set(bid, 'broker')
          leafSize.set(bid, { width: BROKER_CHIP_W, height: BROKER_CHIP_H })
        })
      }
    })
  }

  allRelations
    .filter((r) => r.type === 'RUNS_ON')
    .forEach((r) => {
      const ep = positions.get(r.source)
      if (!ep || positions.has(r.target)) return
      // 若主机已被收进 Kafka 卡片，不再重复挂到程序下
      if (parentOf.has(r.target)) return
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
    const meta = containerMeta.get(n.id)
    const size = leafSize.get(n.id)
    const parentNodeId = parentOf.get(n.id) || null
    return {
      ...n,
      x: p.x,
      y: p.y,
      width: meta?.width ?? size?.width ?? NODE_WIDTH,
      height: meta?.height ?? size?.height ?? NODE_HEIGHT,
      parentNodeId,
      isContainer: !!meta,
      nestRole: nestRole.get(n.id) || null,
    }
  })

  return resolveOverlaps(positioned, parentOf)
}

function nodeHalfSize(n: PositionedNode): { hw: number; hh: number } {
  return {
    hw: (n.width ?? NODE_WIDTH) / 2,
    hh: (n.height ?? NODE_HEIGHT) / 2,
  }
}

function overlaps(a: PositionedNode, b: PositionedNode): boolean {
  const sa = nodeHalfSize(a)
  const sb = nodeHalfSize(b)
  return Math.abs(a.x - b.x) < sa.hw + sb.hw + 24 && Math.abs(a.y - b.y) < sa.hh + sb.hh + 20
}

function resolveOverlaps(
  nodes: PositionedNode[],
  parentOf: Map<string, string> = new Map(),
): PositionedNode[] {
  const result = nodes.map((n) => ({ ...n }))
  const byId = new Map(result.map((n) => [n.id, n]))

  const rootIdOf = (id: string): string => parentOf.get(id) || id

  const moveRoot = (rootId: string, ddx: number, ddy: number) => {
    const root = byId.get(rootId)
    if (!root) return
    root.x += ddx
    root.y += ddy
    result.forEach((child) => {
      if (parentOf.get(child.id) === rootId) {
        child.x += ddx
        child.y += ddy
      }
    })
  }

  for (let iter = 0; iter < 20; iter++) {
    let moved = false
    for (let i = 0; i < result.length; i++) {
      for (let j = i + 1; j < result.length; j++) {
        const a = result[i]
        const b = result[j]
        const ra = rootIdOf(a.id)
        const rb = rootIdOf(b.id)
        // 同一集群卡片内不互推
        if (ra === rb) continue
        if (!overlaps(a, b)) continue

        const dy = (b.y - a.y) || 1
        const sign = dy >= 0 ? 1 : -1
        const nudgeY = 24
        const nudgeX = Math.abs(a.x - b.x) < 8 ? 14 : 0
        moveRoot(ra, -nudgeX, -sign * nudgeY)
        moveRoot(rb, nudgeX, sign * nudgeY)
        moved = true
      }
    }
    if (!moved) break
  }
  return result
}

/** 关系边：CONTAINS / 已收入卡片的 BROKER 不再画线；简洁只画部署 */
export function visibleRelations(
  relations: GraphRelation[] | undefined,
  mode: LayoutMode,
  options?: { compressExecutorHost?: boolean },
): GraphRelation[] {
  const list = relations ?? []
  if (options?.compressExecutorHost) {
    return []
  }
  if (mode === 'compact') {
    return list.filter((r) => r.type === 'RUNS_ON')
  }
  // 完整模式：包含与集群内 Broker 都用卡片表达
  return list.filter((r) => r.type === 'RUNS_ON')
}

/**
 * 压缩程序所属节点：不单独画「部署于」连线与部署主机节点，
 * 改为在程序框内展示主机名称。
 */
export function applyCompressExecutorHost(graph: AssetGraph): AssetGraph {
  const nodeMap = nodeById(graph)
  const runsOn = (graph.relations || []).filter((r) => r.type === 'RUNS_ON')
  if (!runsOn.length) {
    return {
      ...graph,
      relations: (graph.relations || []).filter((r) => r.type !== 'RUNS_ON'),
    }
  }

  const hostLabelsByExecutor = new Map<string, string[]>()
  const deployHostIds = new Set<string>()
  for (const rel of runsOn) {
    deployHostIds.add(rel.target)
    const host = nodeMap.get(rel.target)
    const label = (host?.label || rel.label || '').trim()
    if (!label) continue
    const list = hostLabelsByExecutor.get(rel.source) ?? []
    if (!list.includes(label)) list.push(label)
    hostLabelsByExecutor.set(rel.source, list)
  }

  const flowEndpointIds = new Set<string>()
  graph.edges.forEach((edge) => {
    flowEndpointIds.add(edge.source)
    flowEndpointIds.add(edge.target)
  })

  // 仅作为部署目标、且不是流向端点的主机，压缩后从画布移除
  const removableHosts = new Set(
    [...deployHostIds].filter((id) => {
      if (flowEndpointIds.has(id)) return false
      const node = nodeMap.get(id)
      if (!node) return true
      if (node.kind === 'EXECUTOR') return false
      return node.type === 'HOST'
    }),
  )

  const nodes = graph.nodes
    .filter((n) => !removableHosts.has(n.id))
    .map((n) => {
      if (n.kind !== 'EXECUTOR') return n
      const labels = hostLabelsByExecutor.get(n.id)
      if (!labels?.length) return n
      return { ...n, deployHostLabel: labels.join('、') }
    })

  const nodeIds = new Set(nodes.map((n) => n.id))
  const relations = (graph.relations || []).filter(
    (r) =>
      r.type !== 'RUNS_ON' &&
      nodeIds.has(r.source) &&
      nodeIds.has(r.target),
  )
  const groupIds = new Set(nodes.map((n) => n.groupId).filter(Boolean) as string[])
  const groups = graph.groups.filter((g) => groupIds.has(g.id))

  return { ...graph, nodes, relations, groups }
}

export function executorNodeLabel(node: Pick<GraphNode, 'label' | 'deployHostLabel'>): string {
  const host = node.deployHostLabel?.trim()
  if (host) {
    return `${node.label}\n程序 @ ${host}`
  }
  return `${node.label}\n程序`
}

export function purposeLabel(purpose: string): string {
  const map: Record<string, string> = {
    INGEST: '接入',
    SHARE: '共享',
    SYNC: '同步',
    FORWARD: '转发',
    AUX: '辅助',
    DERIVE: '派生拼接',
  }
  return map[purpose] ?? purpose
}

export function methodLabel(method: string): string {
  const map: Record<string, string> = {
    DIRECT_PUSH: '直推',
    CROSS_ZONE_PUSH: '跨区隔离推送',
    CROSS_ZONE_SEND: '跨区发送',
    CROSS_ZONE_RECV: '跨区接收',
    KAFKA_SUBSCRIBE_FORWARD: '订阅转发',
    NOTIFY_THEN_PULL: '通知+拉取',
    NOTIFY_THEN_SHARED_READ: '通知+共享读取',
    SCRIPT_PULL: '脚本拉取',
    STREAM_JOIN: '拼接加工',
    SFTP_PUSH: 'SFTP推送',
    DIR_WATCH_PUSH: '目录监听推送',
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

export function edgeStroke(edge: Pick<GraphEdge, 'primary' | 'purpose' | 'upstream'>): string {
  if (edge.upstream) return '#0e7490'
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
    case 'DERIVE':
      return '#0e7490'
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
