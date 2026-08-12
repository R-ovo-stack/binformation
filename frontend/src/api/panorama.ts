import { http } from './http'
import type { AssetGraph } from '@/types/graph'
import type { PanoramaGraph } from '@/types/panorama'

export async function getPanoramaGraph(includeEndpointLinks = true): Promise<PanoramaGraph> {
  const { data } = await http.get<PanoramaGraph>('/api/graph/panorama', {
    params: { includeEndpointLinks },
  })
  return data
}

export async function getTechnicalPanoramaGraph(options?: {
  assetIds?: number[]
  includeAuxiliary?: boolean
  includeDerivationBridges?: boolean
}): Promise<AssetGraph> {
  const params: Record<string, unknown> = {
    includeAuxiliary: options?.includeAuxiliary ?? false,
    includeDerivationBridges: options?.includeDerivationBridges ?? true,
  }
  if (options?.assetIds?.length) {
    params.assetIds = options.assetIds
  }
  const { data } = await http.get<AssetGraph>('/api/graph/panorama/technical', { params })
  return data
}
