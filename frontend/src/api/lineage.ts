import { http } from '@/api/http'
import type { AssetDownstreamQuery, SystemAssetQuery, SystemOption } from '@/types/lineage'

export async function listLineageSystems(): Promise<SystemOption[]> {
  const { data } = await http.get<SystemOption[]>('/api/lineage/systems')
  return data
}

export async function getSystemConsumedAssets(
  systemId: number,
  includeAuxiliary = false,
): Promise<SystemAssetQuery> {
  const { data } = await http.get<SystemAssetQuery>(`/api/lineage/systems/${systemId}/assets`, {
    params: { includeAuxiliary },
  })
  return data
}

export async function getAssetDownstreamSystems(
  assetId: number,
  includeAuxiliary = false,
): Promise<AssetDownstreamQuery> {
  const { data } = await http.get<AssetDownstreamQuery>(
    `/api/lineage/assets/${assetId}/downstream-systems`,
    { params: { includeAuxiliary } },
  )
  return data
}
