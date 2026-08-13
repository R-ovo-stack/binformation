import type { EndpointDetail } from '@/types/endpoint'
import { typeLabel } from '@/types/endpoint'

/** 某父类型下可创建的子落点类型 */
const CHILD_TYPES_BY_PARENT: Record<string, string[]> = {
  SECURITY_ZONE: ['SYSTEM'],
  SYSTEM: ['KAFKA', 'ROCKETMQ', 'OBJECT_STORAGE', 'HOST', 'HTTP_API'],
  KAFKA: ['KAFKA_TOPIC'],
  ROCKETMQ: ['ROCKETMQ_TOPIC'],
  OBJECT_STORAGE: ['OBJECT_BUCKET'],
  OBJECT_BUCKET: ['OBJECT_PREFIX'],
  HOST: ['DIRECTORY'],
}

export interface EndpointTreeNode {
  id: number
  label: string
  type: string
  typeLabel: string
  status: string
  zoneName?: string | null
  endpoint: EndpointDetail
  children: EndpointTreeNode[]
}

export function childTypesForParent(parentType: string): string[] {
  return CHILD_TYPES_BY_PARENT[parentType] ?? []
}

export function buildEndpointTree(
  endpoints: EndpointDetail[],
  labels: Record<string, string>,
): EndpointTreeNode[] {
  const byParent = new Map<number | null, EndpointDetail[]>()
  for (const ep of endpoints) {
    const key = ep.parentId ?? null
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

  function build(parentId: number | null): EndpointTreeNode[] {
    return (byParent.get(parentId) ?? []).map((ep) => ({
      id: ep.id,
      label: ep.name,
      type: ep.type,
      typeLabel: typeLabel(ep.type, labels),
      status: ep.status,
      zoneName: ep.zoneName,
      endpoint: ep,
      children: build(ep.id),
    }))
  }

  return build(null)
}

/** 类型或关键字筛选时，保留匹配节点及其祖先 */
export function filterEndpointTree(
  nodes: EndpointTreeNode[],
  typeFilter: string,
  keyword: string,
): EndpointTreeNode[] {
  const kw = keyword.trim().toLowerCase()

  function match(node: EndpointTreeNode): boolean {
    const typeOk = !typeFilter || node.type === typeFilter
    const kwOk =
      !kw ||
      node.label.toLowerCase().includes(kw) ||
      String(node.id).includes(kw) ||
      node.typeLabel.toLowerCase().includes(kw)
    return typeOk && kwOk
  }

  function walk(list: EndpointTreeNode[]): EndpointTreeNode[] {
    const result: EndpointTreeNode[] = []
    for (const node of list) {
      const children = walk(node.children)
      if (match(node) || children.length > 0) {
        result.push({ ...node, children })
      }
    }
    return result
  }

  return walk(nodes)
}

export function collectExpandKeys(nodes: EndpointTreeNode[]): number[] {
  const keys: number[] = []
  function walk(list: EndpointTreeNode[]) {
    for (const n of list) {
      keys.push(n.id)
      if (n.children.length) walk(n.children)
    }
  }
  walk(nodes)
  return keys
}
