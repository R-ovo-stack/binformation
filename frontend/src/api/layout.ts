import { http } from './http'

export interface LayoutNodePayload {
  endpointId: number
  layoutX: number
  layoutY: number
}

export async function saveAssetLayout(assetId: number, nodes: LayoutNodePayload[]): Promise<void> {
  await http.put(`/api/assets/${assetId}/layout`, nodes)
}
