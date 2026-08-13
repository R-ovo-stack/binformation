import { http } from '@/api/http'
import type { ImpactAction, ImpactAnalysis, ImpactEntityType } from '@/types/impact'

export async function analyzeImpact(
  entityType: ImpactEntityType,
  entityId: number,
  action: ImpactAction = 'DELETE',
): Promise<ImpactAnalysis> {
  const { data } = await http.get<ImpactAnalysis>('/api/impact', {
    params: { entityType, entityId, action },
  })
  return data
}
