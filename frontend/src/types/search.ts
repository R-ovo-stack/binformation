export interface SearchHit {
  entityType: string
  entityId: number
  label: string
  subtitle: string | null
  assetId: number | null
  assetName: string | null
  flowId: number | null
  endpointId: number | null
}

export interface SearchGroup {
  entityType: string
  label: string
  count: number
  items: SearchHit[]
}

export interface SearchResult {
  query: string
  total: number
  groups: SearchGroup[]
}

export const SEARCH_ENTITY_LABELS: Record<string, string> = {
  ASSET: '数据资产',
  ENDPOINT: '落点',
  FLOW: '流向',
  EXECUTOR: '程序/脚本',
  DERIVATION: '派生',
}
