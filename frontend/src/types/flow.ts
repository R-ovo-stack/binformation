export type FlowPurpose = 'INGEST' | 'SHARE' | 'SYNC' | 'FORWARD' | 'AUX'
export type FlowStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'DEPRECATED'
export type FlowStepMethod =
  | 'DIRECT_PUSH'
  | 'CROSS_ZONE_PUSH'
  | 'CROSS_ZONE_SEND'
  | 'CROSS_ZONE_RECV'
  | 'KAFKA_SUBSCRIBE_FORWARD'
  | 'NOTIFY_THEN_PULL'
  | 'NOTIFY_THEN_SHARED_READ'
  | 'SCRIPT_PULL'
  | 'STREAM_JOIN'
  | 'SFTP_PUSH'
  | 'DIR_WATCH_PUSH'
  | 'OTHER'

export interface FlowStep {
  id?: number | null
  seq: number
  hostId?: number | null
  hostLabel?: string | null
  executorId: number | null
  executorName?: string | null
  method: FlowStepMethod | string
  remark?: string | null
}

export interface FlowPath {
  id?: number | null
  name: string
  enabled: boolean
  sortOrder: number
  remark?: string | null
  steps: FlowStep[]
}

export interface FlowSummary {
  id: number
  assetId: number
  sourceEndpointId: number
  sourceEndpointLabel: string
  targetEndpointId: number
  targetEndpointLabel: string
  purpose: FlowPurpose | string
  primary: boolean
  status: FlowStatus | string
  remark?: string | null
  pathCount: number
  stepCount: number
}

export interface FlowDetail {
  id?: number
  assetId: number
  assetName?: string
  sourceEndpointId: number | null
  sourceEndpointLabel?: string
  targetEndpointId: number | null
  targetEndpointLabel?: string
  purpose: FlowPurpose | string
  primary: boolean
  status: FlowStatus | string
  owner?: string | null
  remark?: string | null
  paths: FlowPath[]
}

export interface FlowSavePayload {
  sourceEndpointId: number
  targetEndpointId: number
  purpose: string
  primary: boolean
  status: string
  owner?: string | null
  remark?: string | null
  paths: Array<{
    name: string
    enabled: boolean
    sortOrder: number
    remark?: string | null
    steps: Array<{
      seq: number
      hostId?: number | null
      executorId: number
      method: string
      remark?: string | null
    }>
  }>
}

export interface EndpointOption {
  id: number
  type: string
  name: string
  breadcrumb: string
  zoneId?: number | null
  zoneName?: string | null
}

export interface ExecutorOption {
  id: number
  name: string
  code: string
  kind: string
  defaultHostId?: number | null
  defaultHostLabel?: string | null
}

export const FLOW_PURPOSE_OPTIONS: Array<{ value: FlowPurpose; label: string }> = [
  { value: 'INGEST', label: '接入' },
  { value: 'SHARE', label: '共享/供给' },
  { value: 'SYNC', label: '同步' },
  { value: 'FORWARD', label: '转发' },
  { value: 'AUX', label: '辅助流向' },
]

export const FLOW_STATUS_OPTIONS: Array<{ value: FlowStatus; label: string }> = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ACTIVE', label: '启用' },
  { value: 'INACTIVE', label: '停用' },
  { value: 'DEPRECATED', label: '废弃' },
]

export const FLOW_METHOD_OPTIONS: Array<{ value: FlowStepMethod; label: string }> = [
  { value: 'DIRECT_PUSH', label: '直推' },
  { value: 'CROSS_ZONE_PUSH', label: '跨区隔离推送' },
  { value: 'CROSS_ZONE_SEND', label: '跨区发送' },
  { value: 'CROSS_ZONE_RECV', label: '跨区接收' },
  { value: 'KAFKA_SUBSCRIBE_FORWARD', label: '订阅转发' },
  { value: 'NOTIFY_THEN_PULL', label: '通知+拉取' },
  { value: 'NOTIFY_THEN_SHARED_READ', label: '通知+共享读取' },
  { value: 'SCRIPT_PULL', label: '脚本拉取' },
  { value: 'STREAM_JOIN', label: '拼接加工' },
  { value: 'SFTP_PUSH', label: 'SFTP推送' },
  { value: 'DIR_WATCH_PUSH', label: '目录监听推送' },
  { value: 'OTHER', label: '其他' },
]

export function purposeLabel(purpose: string): string {
  return FLOW_PURPOSE_OPTIONS.find((o) => o.value === purpose)?.label ?? purpose
}

export function methodLabel(method: string): string {
  return FLOW_METHOD_OPTIONS.find((o) => o.value === method)?.label ?? method
}

export function emptyPath(sortOrder = 0): FlowPath {
  return {
    name: '默认路径',
    enabled: true,
    sortOrder,
    remark: null,
    steps: [emptyStep(1)],
  }
}

export function emptyStep(seq: number): FlowStep {
  return {
    seq,
    hostId: null,
    executorId: null,
    method: 'DIRECT_PUSH',
    remark: null,
  }
}

export function emptyFlow(assetId: number): FlowDetail {
  return {
    assetId,
    sourceEndpointId: null,
    targetEndpointId: null,
    purpose: 'SHARE',
    primary: true,
    status: 'ACTIVE',
    owner: null,
    remark: null,
    paths: [emptyPath(0)],
  }
}

export function toSavePayload(form: FlowDetail): FlowSavePayload {
  return {
    sourceEndpointId: form.sourceEndpointId!,
    targetEndpointId: form.targetEndpointId!,
    purpose: form.purpose,
    primary: form.primary,
    status: form.status,
    owner: form.owner,
    remark: form.remark,
    paths: form.paths.map((path, index) => ({
      name: path.name,
      enabled: path.enabled,
      sortOrder: path.sortOrder ?? index,
      remark: path.remark,
      steps: path.steps.map((step, stepIndex) => ({
        seq: step.seq ?? stepIndex + 1,
        hostId: step.hostId,
        executorId: step.executorId!,
        method: step.method,
        remark: step.remark,
      })),
    })),
  }
}
