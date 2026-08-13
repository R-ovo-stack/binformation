import { http } from './http'
import type { ChangeLogEntry } from '@/types/changelog'

export async function listChangeLogsByAsset(assetId: number): Promise<ChangeLogEntry[]> {
  const { data } = await http.get<ChangeLogEntry[]>(`/api/assets/${assetId}/change-logs`)
  return data
}
