import type { RouteLocationRaw } from 'vue-router'
import type { ImpactItem } from '@/types/impact'

/** Resolve a route for an impact item so users can jump to the related entity. */
export function resolveImpactItemRoute(item: ImpactItem): RouteLocationRaw | null {
  const type = (item.entityType || '').toUpperCase()

  switch (type) {
    case 'FLOW':
    case 'FLOW_STEP':
      if (item.assetId != null && item.flowId != null) {
        return {
          name: 'flow-visual-edit',
          params: { id: String(item.assetId), flowId: String(item.flowId) },
        }
      }
      if (item.assetId != null) {
        return { name: 'asset-flows', params: { id: String(item.assetId) } }
      }
      return null
    case 'LAYOUT':
      if (item.assetId != null) {
        return { name: 'asset-graph', params: { id: String(item.assetId) } }
      }
      return null
    case 'ASSET': {
      const assetId = item.assetId ?? item.id
      if (assetId != null) {
        return { name: 'asset-edit', params: { id: String(assetId) } }
      }
      return null
    }
    case 'ENDPOINT': {
      const endpointId = item.endpointId ?? item.id
      if (endpointId != null) {
        return { name: 'endpoint-edit', params: { id: String(endpointId) } }
      }
      return null
    }
    case 'EXECUTOR':
      if (item.id != null) {
        return { name: 'executor-edit', params: { id: String(item.id) } }
      }
      return null
    case 'DERIVATION':
      if (item.id != null && item.assetId != null) {
        return {
          name: 'derivation-edit',
          params: { id: String(item.assetId), derivationId: String(item.id) },
        }
      }
      return null
    default:
      if (item.flowId != null && item.assetId != null) {
        return {
          name: 'flow-visual-edit',
          params: { id: String(item.assetId), flowId: String(item.flowId) },
        }
      }
      if (item.endpointId != null) {
        return { name: 'endpoint-edit', params: { id: String(item.endpointId) } }
      }
      if (item.assetId != null) {
        return { name: 'asset-edit', params: { id: String(item.assetId) } }
      }
      return null
  }
}

export function impactItemLinkLabel(item: ImpactItem): string {
  const type = (item.entityType || '').toUpperCase()
  switch (type) {
    case 'FLOW':
    case 'FLOW_STEP':
      return '打开流向'
    case 'LAYOUT':
      return '打开资产图'
    case 'ASSET':
      return '打开资产'
    case 'ENDPOINT':
      return '打开落点'
    case 'EXECUTOR':
      return '打开程序'
    case 'DERIVATION':
      return '打开派生'
    default:
      return '查看'
  }
}
