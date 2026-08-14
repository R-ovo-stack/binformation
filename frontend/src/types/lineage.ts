export interface LineageEndpointRef {
  id: number
  name: string
  type: string | null
  breadcrumb: string | null
}

export interface LineageFlowRef {
  id: number
  assetId: number
  assetName: string | null
  purpose: string
  status: string
  primary: boolean
  source: LineageEndpointRef
  target: LineageEndpointRef
}

export interface SystemOption {
  id: number
  name: string
  breadcrumb: string
  zoneName: string | null
  status: string
}

export interface SystemConsumedAsset {
  assetId: number
  assetName: string
  assetCode: string
  dataType: string
  status: string
  role: string
  flows: LineageFlowRef[]
}

export interface SystemAssetQuery {
  systemId: number
  systemName: string
  systemBreadcrumb: string
  zoneName: string | null
  assetCount: number
  assets: SystemConsumedAsset[]
}

export interface DownstreamSystem {
  systemId: number | null
  systemName: string
  systemBreadcrumb: string | null
  zoneName: string | null
  role: string
  flows: LineageFlowRef[]
}

export interface AssetDownstreamQuery {
  assetId: number
  assetName: string
  assetCode: string
  dataType: string
  systemCount: number
  systems: DownstreamSystem[]
}

export const LINEAGE_ROLE_LABELS: Record<string, string> = {
  CONSUMER: '消费/供给目标',
  INGEST_TARGET: '接入目标',
  SYNC_TARGET: '同步目标',
  FORWARD_TARGET: '转发目标',
  TARGET: '流向目标',
  UNKNOWN: '未归属系统',
}
