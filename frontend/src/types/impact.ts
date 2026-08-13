export type ImpactEntityType = 'ENDPOINT' | 'ASSET' | 'FLOW' | 'EXECUTOR' | 'DERIVATION'
export type ImpactAction = 'DELETE' | 'UPDATE'
export type ImpactSeverity = 'BLOCKER' | 'WARNING' | 'INFO'

export interface ImpactItem {
  id: number | null
  label: string
  entityType: string
  assetId: number | null
  assetName: string | null
  flowId: number | null
  endpointId: number | null
  role: string | null
  detail: string | null
}

export interface ImpactGroup {
  kind: string
  severity: ImpactSeverity
  count: number
  message: string
  items: ImpactItem[]
}

export interface ImpactAnalysis {
  entityType: ImpactEntityType
  entityId: number
  entityLabel: string
  action: ImpactAction
  canProceed: boolean
  summary: string
  blockers: ImpactGroup[]
  warnings: ImpactGroup[]
}
