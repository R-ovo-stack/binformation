export interface ChangeLogItem {
  fieldName: string
  oldValue?: string | null
  newValue?: string | null
}

export interface ChangeLogEntry {
  id: number
  entityType: string
  entityId: number
  action: string
  summary: string
  operator: string
  operatedAt: string
  relatedAssetId?: number | null
  remark?: string | null
  items: ChangeLogItem[]
}
