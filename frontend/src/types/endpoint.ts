import type { EntityStatus } from '@/types/asset'

export interface EndpointDetail {
  id: number
  type: string
  name: string
  code?: string | null
  parentId?: number | null
  parentName?: string | null
  zoneId?: number | null
  zoneName?: string | null
  breadcrumb: string
  attrs?: string | null
  status: EntityStatus | string
  owner?: string | null
  remark?: string | null
}

export interface EndpointSavePayload {
  type: string
  name: string
  code?: string | null
  parentId?: number | null
  attrs?: string | null
  status: string
  owner?: string | null
  remark?: string | null
}

export interface EndpointTypeMeta {
  types: string[]
  labels: Record<string, string>
}

export function emptyEndpointForm(type = 'KAFKA_TOPIC'): EndpointSavePayload {
  return {
    type,
    name: '',
    code: null,
    parentId: null,
    attrs: null,
    status: 'ACTIVE',
    owner: null,
    remark: null,
  }
}

export function typeLabel(type: string, labels?: Record<string, string>): string {
  return labels?.[type] ?? type
}
