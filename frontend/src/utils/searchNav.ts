import type { RouteLocationRaw } from 'vue-router'
import type { SearchHit } from '@/types/search'
import { resolveImpactItemRoute } from '@/utils/impactNav'
import type { ImpactItem } from '@/types/impact'

function asImpactItem(hit: SearchHit): ImpactItem {
  return {
    id: hit.entityId,
    label: hit.label,
    entityType: hit.entityType,
    assetId: hit.assetId,
    assetName: hit.assetName,
    flowId: hit.flowId,
    endpointId: hit.endpointId,
    role: null,
    detail: hit.subtitle,
  }
}

export function resolveSearchHitRoute(hit: SearchHit): RouteLocationRaw | null {
  return resolveImpactItemRoute(asImpactItem(hit))
}

export function searchPageQuery(query: string) {
  return {
    name: 'search' as const,
    query: { q: query.trim() },
  }
}
