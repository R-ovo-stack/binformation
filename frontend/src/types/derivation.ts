export interface DerivationInputItem {
  inputAssetId: number
  inputAssetName?: string | null
  sortOrder: number
}

export interface DerivationDetail {
  id: number
  name: string
  outputAssetId: number
  outputAssetName?: string | null
  executorId: number
  executorName?: string | null
  hostId?: number | null
  hostLabel?: string | null
  status: string
  owner?: string | null
  remark?: string | null
  inputs: DerivationInputItem[]
}

export interface DerivationSavePayload {
  name: string
  executorId: number
  hostId?: number | null
  status: string
  owner?: string | null
  remark?: string | null
  inputs: Array<{ inputAssetId: number; sortOrder: number }>
}

export function emptyDerivationForm(): DerivationSavePayload {
  return {
    name: '',
    executorId: 0,
    hostId: null,
    status: 'ACTIVE',
    owner: null,
    remark: null,
    inputs: [{ inputAssetId: 0, sortOrder: 0 }],
  }
}
