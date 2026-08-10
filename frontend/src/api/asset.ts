import { http } from './http'
import type { AssetGraph, DataAsset } from '@/types/graph'

export async function listAssets(): Promise<DataAsset[]> {
  const { data } = await http.get<DataAsset[]>('/api/assets')
  return data
}

export async function getAsset(id: number): Promise<DataAsset> {
  const { data } = await http.get<DataAsset>(`/api/assets/${id}`)
  return data
}

export async function getAssetGraph(
  id: number,
  includeAuxiliary = false,
): Promise<AssetGraph> {
  const { data } = await http.get<AssetGraph>(`/api/assets/${id}/graph`, {
    params: { includeAuxiliary },
  })
  return data
}
