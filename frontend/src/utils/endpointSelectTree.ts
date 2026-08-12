import type { EndpointOption } from '@/types/flow'
import { typeLabel } from '@/types/endpoint'

export interface EndpointSelectTreeNode {
  id: number
  label: string
  type: string
  typeLabel: string
  breadcrumb: string
  filterText: string
  disabled?: boolean
  children?: EndpointSelectTreeNode[]
}

/** 将扁平落点选项组装为 el-tree-select 数据 */
export function buildEndpointSelectTree(
  options: EndpointOption[],
  labels: Record<string, string> = {},
): EndpointSelectTreeNode[] {
  const byId = new Map(options.map((ep) => [ep.id, ep]))
  const byParent = new Map<number | null, EndpointOption[]>()

  for (const ep of options) {
    const parentId = ep.parentId ?? null
    const key = parentId != null && byId.has(parentId) ? parentId : null
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key)!.push(ep)
  }

  for (const list of byParent.values()) {
    list.sort((a, b) => {
      const typeCmp = a.type.localeCompare(b.type)
      if (typeCmp !== 0) return typeCmp
      return a.name.localeCompare(b.name, 'zh-CN')
    })
  }

  function build(parentId: number | null): EndpointSelectTreeNode[] {
    return (byParent.get(parentId) ?? []).map((ep) => {
      const tLabel = typeLabel(ep.type, labels)
      const children = build(ep.id)
      return {
        id: ep.id,
        label: ep.name,
        type: ep.type,
        typeLabel: tLabel,
        breadcrumb: ep.breadcrumb,
        filterText: `${ep.breadcrumb} ${ep.name} ${tLabel} ${ep.type}`.toLowerCase(),
        children: children.length ? children : undefined,
      }
    })
  }

  return build(null)
}

export function endpointOptionLabel(ep: EndpointOption, labels: Record<string, string> = {}): string {
  return `${ep.breadcrumb} / ${ep.name} (${typeLabel(ep.type, labels)})`
}
