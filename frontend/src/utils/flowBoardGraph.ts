import type {
  AssetGraph,
  GraphEdge,
  GraphGroup,
  GraphNode,
  GraphPath,
  GraphRelation,
} from '@/types/graph'
import type { EndpointOption, ExecutorOption, FlowDetail } from '@/types/flow'

export interface BoardFlowEdge {
  id: string
  flowId?: number | null
  sourceEndpointId: number
  targetEndpointId: number
  purpose: string
  primary: boolean
  pathCount?: number
  draft?: boolean
}

function epNodeId(id: number): string {
  return `ep-${id}`
}

function execNodeId(id: number): string {
  return `exec-${id}`
}

function zoneGroupId(zoneId: number): string {
  return `group-${zoneId}`
}

function resolveFlowDetail(
  edge: BoardFlowEdge,
  flowDetailsById: Record<number, FlowDetail>,
  draft: FlowDetail | null,
): FlowDetail | null {
  if (edge.draft) return draft
  if (edge.flowId != null) return flowDetailsById[edge.flowId] ?? null
  return null
}

function toGraphPaths(detail: FlowDetail | null, executors: ExecutorOption[]): GraphPath[] {
  if (!detail?.paths?.length) return []
  return detail.paths.map((p, index) => ({
    pathId: p.id ?? index,
    name: p.name,
    enabled: p.enabled,
    sortOrder: p.sortOrder ?? index,
    steps: p.steps
      .filter((s) => s.executorId != null)
      .map((s) => ({
        seq: s.seq,
        hostId: s.hostId ?? null,
        hostLabel: s.hostLabel ?? null,
        executorId: s.executorId!,
        executorName: executors.find((e) => e.id === s.executorId)?.name ?? s.executorName ?? null,
        method: s.method,
        remark: s.remark ?? null,
      })),
  }))
}

/** 将可视化编辑画布状态转为与一键成图相同的 AssetGraph，便于复用布局与样式 */
export function buildBoardAssetGraph(params: {
  asset: { id: number; name: string; code: string; dataType?: string }
  canvasEndpointIds: Set<number>
  allEndpoints: EndpointOption[]
  boardEdges: BoardFlowEdge[]
  flowDetailsById: Record<number, FlowDetail>
  draft: FlowDetail | null
  executors: ExecutorOption[]
}): AssetGraph {
  const {
    asset,
    canvasEndpointIds,
    allEndpoints,
    boardEdges,
    flowDetailsById,
    draft,
    executors,
  } = params

  const epById = new Map(allEndpoints.map((e) => [e.id, e]))
  const executorById = new Map(executors.map((e) => [e.id, e]))
  const nodeIds = new Set<string>()
  const nodes: GraphNode[] = []
  const relations: GraphRelation[] = []
  const relationIds = new Set<string>()
  const groups: GraphGroup[] = []
  const groupSeen = new Set<number>()

  function ensureEndpointNode(endpointId: number) {
    const id = epNodeId(endpointId)
    if (nodeIds.has(id)) return
    const ep = epById.get(endpointId)
    if (!ep) return
    if (ep.zoneId != null && !groupSeen.has(ep.zoneId)) {
      groupSeen.add(ep.zoneId)
      groups.push({
        id: zoneGroupId(ep.zoneId),
        zoneEndpointId: ep.zoneId,
        label: ep.zoneName || ep.name,
      })
    }
    nodes.push({
      id,
      kind: 'ENDPOINT',
      endpointId: ep.id,
      type: ep.type,
      label: ep.name,
      groupId: ep.zoneId != null ? zoneGroupId(ep.zoneId) : null,
      breadcrumb: ep.breadcrumb,
    })
    nodeIds.add(id)
  }

  function ensureExecutorNode(executorId: number) {
    const id = execNodeId(executorId)
    if (nodeIds.has(id)) return
    const ex = executorById.get(executorId)
    nodes.push({
      id,
      kind: 'EXECUTOR',
      executorId,
      type: ex?.kind ?? 'PROGRAM',
      label: ex?.name ?? `程序#${executorId}`,
      groupId: null,
      breadcrumb: ex?.code,
    })
    nodeIds.add(id)
  }

  function addRunsOn(executorId: number, hostId: number) {
    const relId = `rel-runs-on-${executorId}-${hostId}`
    if (relationIds.has(relId)) return
    relationIds.add(relId)
    relations.push({
      id: relId,
      source: execNodeId(executorId),
      target: epNodeId(hostId),
      type: 'RUNS_ON',
      label: '部署于',
    })
  }

  function addContains(parentId: number, childId: number, label = '包含') {
    const relId = `rel-contains-${parentId}-${childId}`
    if (relationIds.has(relId)) return
    relationIds.add(relId)
    relations.push({
      id: relId,
      source: epNodeId(parentId),
      target: epNodeId(childId),
      type: 'CONTAINS',
      label,
    })
  }

  canvasEndpointIds.forEach((endpointId) => ensureEndpointNode(endpointId))

  // 补齐主题/目录所属集群，并建立 CONTAINS，便于成图嵌套
  const topicLike = new Set(['KAFKA_TOPIC', 'ROCKETMQ_TOPIC', 'OBJECT_BUCKET', 'OBJECT_PREFIX', 'DIRECTORY'])
  canvasEndpointIds.forEach((endpointId) => {
    const ep = epById.get(endpointId)
    if (!ep?.parentId || !topicLike.has(ep.type)) return
    const parent = epById.get(ep.parentId)
    if (!parent) return
    ensureEndpointNode(parent.id)
    const label =
      ep.type === 'KAFKA_TOPIC' || ep.type === 'ROCKETMQ_TOPIC'
        ? '包含主题'
        : ep.type === 'DIRECTORY'
          ? '包含目录'
          : '包含'
    addContains(parent.id, ep.id, label)
  })

  const edges: GraphEdge[] = boardEdges.map((edge) => {
    const detail = resolveFlowDetail(edge, flowDetailsById, draft)
    const paths = toGraphPaths(detail, executors)

    for (const path of paths) {
      for (const step of path.steps) {
        ensureExecutorNode(step.executorId)
        if (step.hostId != null) {
          ensureEndpointNode(step.hostId)
          addRunsOn(step.executorId, step.hostId)
        }
      }
    }

    return {
      id: edge.id,
      flowId: edge.flowId ?? null,
      source: epNodeId(edge.sourceEndpointId),
      target: epNodeId(edge.targetEndpointId),
      purpose: edge.purpose,
      primary: edge.primary,
      status: detail?.status ?? 'ACTIVE',
      remark: detail?.remark ?? null,
      paths,
    }
  })

  groups.sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))

  return {
    assetId: asset.id,
    assetName: asset.name,
    assetCode: asset.code,
    dataType: asset.dataType ?? 'FILE',
    groups,
    nodes,
    edges,
    relations,
    derivations: [],
    hasUpstream: false,
  }
}

export function boardFlowIdFromCell(edgeId: string): string {
  const hash = edgeId.indexOf('#')
  return hash >= 0 ? edgeId.slice(0, hash) : edgeId
}

export function isBoardEndpointNode(cellId: string): boolean {
  return /^ep-\d+$/.test(cellId)
}
