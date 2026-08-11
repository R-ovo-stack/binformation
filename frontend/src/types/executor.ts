export type ExecutorKind = 'PROGRAM' | 'SCRIPT'

export interface ExecutorDetail {
  id: number
  name: string
  code: string
  kind: ExecutorKind | string
  defaultHostId?: number | null
  defaultHostLabel?: string | null
  status: string
  owner?: string | null
  remark?: string | null
}

export interface ExecutorSavePayload {
  name: string
  code: string
  kind: string
  defaultHostId?: number | null
  status: string
  owner?: string | null
  remark?: string | null
}

export const EXECUTOR_KIND_OPTIONS = [
  { value: 'PROGRAM', label: '程序' },
  { value: 'SCRIPT', label: '脚本' },
] as const

export function emptyExecutorForm(): ExecutorSavePayload {
  return {
    name: '',
    code: '',
    kind: 'PROGRAM',
    defaultHostId: null,
    status: 'ACTIVE',
    owner: null,
    remark: null,
  }
}
