export interface DataAsset {
  id: number
  name: string
  code: string
  dataType: string
  status: string
  owner?: string
  remark?: string
}

export interface GraphGroup {
  id: string
  zoneEndpointId: number
  label: string
}

export interface GraphNode {
  id: string
  kind?: 'ENDPOINT' | 'EXECUTOR' | string
  endpointId?: number | null
  executorId?: number | null
  type: string
  label: string
  groupId?: string | null
  breadcrumb?: string
  layoutX?: number | null
  layoutY?: number | null
  /** 压缩部署主机时，写入程序框展示的主机名 */
  deployHostLabel?: string | null
}

export interface GraphStep {
  seq: number
  hostId?: number | null
  hostLabel?: string | null
  executorId: number
  executorName?: string | null
  method: string
  remark?: string | null
}

export interface GraphPath {
  pathId: number
  name: string
  enabled: boolean
  sortOrder: number
  steps: GraphStep[]
}

export interface GraphEdge {
  id: string
  flowId?: number | null
  source: string
  target: string
  purpose: string
  primary: boolean
  status: string
  remark?: string | null
  paths: GraphPath[]
  /** 来自派生输入资产的前置流程或派生桥接边 */
  upstream?: boolean
  fromAssetId?: number | null
  fromAssetName?: string | null
}

export interface GraphDerivationInput {
  assetId: number
  assetName?: string | null
  sortOrder: number
}

export interface GraphDerivation {
  derivationId: number
  name: string
  status: string
  outputAssetId: number
  outputAssetName?: string | null
  inputs: GraphDerivationInput[]
  executorId: number
  executorName?: string | null
  hostId?: number | null
  hostLabel?: string | null
}

export interface GraphRelation {
  id: string
  source: string
  target: string
  type: string
  label?: string
}

export interface AssetGraph {
  assetId: number
  assetName: string
  assetCode: string
  dataType: string
  groups: GraphGroup[]
  nodes: GraphNode[]
  edges: GraphEdge[]
  relations?: GraphRelation[]
  derivations: GraphDerivation[]
  /** 当前资产是否为派生输出，可切换展示前置资产流程 */
  hasUpstream?: boolean
}
