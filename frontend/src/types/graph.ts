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
  endpointId: number
  type: string
  label: string
  groupId?: string | null
  breadcrumb?: string
  layoutX?: number | null
  layoutY?: number | null
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
  flowId: number
  source: string
  target: string
  purpose: string
  primary: boolean
  status: string
  remark?: string | null
  paths: GraphPath[]
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
}
