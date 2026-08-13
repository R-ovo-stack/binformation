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

const ENTITY_TYPE_LABELS: Record<string, string> = {
  DATA_ASSET: '数据资产',
  ENDPOINT: '落点',
  FLOW: '流向',
  EXECUTOR: '程序/脚本',
  DERIVATION: '派生',
  FLOW_LAYOUT: '布局',
}

const ACTION_LABELS: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  DELETE: '删除',
}

export function changeEntityLabel(entityType: string): string {
  return ENTITY_TYPE_LABELS[entityType] ?? entityType
}

export function changeActionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}
