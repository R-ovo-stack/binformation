import { http } from './http'
import type { AssetSavePayload } from '@/types/asset'
import type { AssetGraph, DataAsset } from '@/types/graph'

export async function listAssets(): Promise<DataAsset[]> {
  const { data } = await http.get<DataAsset[]>('/api/assets')
  return data
}

export async function getAsset(id: number): Promise<DataAsset> {
  const { data } = await http.get<DataAsset>(`/api/assets/${id}`)
  return data
}

export async function createAsset(payload: AssetSavePayload): Promise<DataAsset> {
  const { data } = await http.post<DataAsset>('/api/assets', payload)
  return data
}

export async function updateAsset(id: number, payload: AssetSavePayload): Promise<DataAsset> {
  const { data } = await http.put<DataAsset>(`/api/assets/${id}`, payload)
  return data
}

export async function deleteAsset(id: number): Promise<void> {
  await http.delete(`/api/assets/${id}`)
}

export async function getAssetGraph(
  id: number,
  includeAuxiliary = false,
  includeUpstream = false,
): Promise<AssetGraph> {
  const { data } = await http.get<AssetGraph>(`/api/assets/${id}/graph`, {
    params: { includeAuxiliary, includeUpstream },
  })
  return data
}
