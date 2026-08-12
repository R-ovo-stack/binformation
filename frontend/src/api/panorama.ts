import { http } from './http'
import type { PanoramaGraph } from '@/types/panorama'

export async function getPanoramaGraph(includeEndpointLinks = true): Promise<PanoramaGraph> {
  const { data } = await http.get<PanoramaGraph>('/api/graph/panorama', {
    params: { includeEndpointLinks },
  })
  return data
}
