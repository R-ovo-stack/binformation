export type AssetDataType = 'FILE' | 'KAFKA_MESSAGE'
export type EntityStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'DEPRECATED'

export interface AssetSavePayload {
  name: string
  code: string
  dataType: string
  status: string
  owner?: string | null
  remark?: string | null
}

export const ASSET_DATA_TYPE_OPTIONS = [
  { value: 'FILE', label: '文件' },
  { value: 'KAFKA_MESSAGE', label: 'Kafka消息' },
] as const

export const ENTITY_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '启用' },
  { value: 'INACTIVE', label: '停用' },
  { value: 'DEPRECATED', label: '废弃' },
] as const

export function emptyAssetForm(): AssetSavePayload {
  return {
    name: '',
    code: '',
    dataType: 'FILE',
    status: 'ACTIVE',
    owner: null,
    remark: null,
  }
}
