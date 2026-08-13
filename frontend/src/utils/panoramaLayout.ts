import type { PanoramaAssetNode, PanoramaEdge, PanoramaGraph, PositionedPanoramaNode } from '@/types/panorama'

const NODE_W = 200
const NODE_H = 88
const COL_GAP = 280
const ROW_GAP = 120
const ISLAND_COL_GAP = 220

function nodeId(assetId: number): string {
  return `asset-${assetId}`
}

function assignLayers(nodeIds: number[], edges: PanoramaEdge[]): Map<number, number> {
  const ids = nodeIds.map(String)
  const idSet = new Set(ids)
  const preds = new Map<string, string[]>()
  const succs = new Map<string, string[]>()
  ids.forEach((id) => {
    preds.set(id, [])
    succs.set(id, [])
  })

  edges.forEach((e) => {
    const s = String(e.sourceAssetId)
    const t = String(e.targetAssetId)
    if (!idSet.has(s) || !idSet.has(t) || s === t) return
    succs.get(s)!.push(t)
    preds.get(t)!.push(s)
  })

  const layer = new Map<string, number>()
  const indeg = new Map<string, number>()
  ids.forEach((id) => indeg.set(id, preds.get(id)!.length))

  const queue = ids.filter((id) => (indeg.get(id) || 0) === 0)
  queue.forEach((id) => layer.set(id, 0))

  const visited = new Set<string>()
  while (queue.length) {
    const cur = queue.shift()!
    if (visited.has(cur)) continue
    visited.add(cur)
    const base = layer.get(cur) || 0
    for (const next of succs.get(cur) || []) {
      layer.set(next, Math.max(layer.get(next) || 0, base + 1))
      indeg.set(next, (indeg.get(next) || 1) - 1)
      if ((indeg.get(next) || 0) <= 0) queue.push(next)
    }
  }

  ids.forEach((id) => {
    if (!layer.has(id)) {
      const ps = (preds.get(id) || [])
        .map((p) => layer.get(p))
        .filter((v): v is number => v != null)
      layer.set(id, ps.length ? Math.max(...ps) + 1 : 0)
    }
  })

  const result = new Map<number, number>()
  layer.forEach((l, id) => result.set(Number(id), l))
  return result
}

/** 血缘全景：有边节点分层左→右，孤立节点底部横排 */
export function layoutPanoramaGraph(graph: PanoramaGraph): PositionedPanoramaNode[] {
  const nodes = graph.nodes
  if (!nodes.length) return []

  const connected = new Set<number>()
  graph.edges.forEach((e) => {
    connected.add(e.sourceAssetId)
    connected.add(e.targetAssetId)
  })

  const linked = nodes.filter((n) => connected.has(n.assetId))
  const isolated = nodes.filter((n) => !connected.has(n.assetId))

  const layers = assignLayers(
    linked.map((n) => n.assetId),
    graph.edges,
  )

  const byLayer = new Map<number, PanoramaAssetNode[]>()
  linked.forEach((n) => {
    const l = layers.get(n.assetId) || 0
    if (!byLayer.has(l)) byLayer.set(l, [])
    byLayer.get(l)!.push(n)
  })

  const result: PositionedPanoramaNode[] = []
  const sortedLayers = [...byLayer.keys()].sort((a, b) => a - b)
  let maxRows = 1

  sortedLayers.forEach((layerNo) => {
    const list = [...(byLayer.get(layerNo) || [])].sort((a, b) =>
      a.name.localeCompare(b.name, 'zh-CN'),
    )
    maxRows = Math.max(maxRows, list.length)
    list.forEach((n, i) => {
      result.push({
        ...n,
        x: 120 + layerNo * COL_GAP + NODE_W / 2,
        y: 80 + i * ROW_GAP + NODE_H / 2,
        width: NODE_W,
        height: NODE_H,
      })
    })
  })

  const islandY = 80 + maxRows * ROW_GAP + 60
  isolated
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    .forEach((n, i) => {
      result.push({
        ...n,
        x: 120 + i * ISLAND_COL_GAP + NODE_W / 2,
        y: islandY + NODE_H / 2,
        width: NODE_W,
        height: NODE_H,
      })
    })

  return result
}

export { nodeId as panoramaNodeId, NODE_W as PANORAMA_NODE_W, NODE_H as PANORAMA_NODE_H }
