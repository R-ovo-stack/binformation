import type { EndpointOption } from '@/types/flow'
import { endpointTypeLabel } from '@/utils/graphLayout'

export interface BoardNodeLayout {
  id: number
  x: number
  y: number
  width: number
  height: number
  label: string
  type: string
  typeLabel: string
  breadcrumb: string
}

const NODE_W = 168
const NODE_H = 52
const COL_GAP = 220
const ROW_GAP = 72
const ZONE_GAP = 48

/** 按安全区分行、区内按类型排布落点 */
export function layoutBoardEndpoints(endpoints: EndpointOption[]): BoardNodeLayout[] {
  if (!endpoints.length) return []

  const byZone = new Map<string, EndpointOption[]>()
  for (const ep of endpoints) {
    const key = ep.zoneName || ep.zoneId?.toString() || '未分区'
    if (!byZone.has(key)) byZone.set(key, [])
    byZone.get(key)!.push(ep)
  }

  const result: BoardNodeLayout[] = []
  let zoneY = 40

  const zones = [...byZone.entries()].sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
  for (const [, list] of zones) {
    list.sort((a, b) => {
      const t = a.type.localeCompare(b.type)
      if (t !== 0) return t
      return a.name.localeCompare(b.name, 'zh-CN')
    })
    const cols = Math.max(1, Math.ceil(Math.sqrt(list.length)))
    list.forEach((ep, i) => {
      const col = i % cols
      const row = Math.floor(i / cols)
      result.push({
        id: ep.id,
        x: 40 + col * COL_GAP + NODE_W / 2,
        y: zoneY + row * ROW_GAP + NODE_H / 2,
        width: NODE_W,
        height: NODE_H,
        label: ep.name,
        type: ep.type,
        typeLabel: endpointTypeLabel(ep.type),
        breadcrumb: ep.breadcrumb,
      })
    })
    const rows = Math.ceil(list.length / cols)
    zoneY += rows * ROW_GAP + ZONE_GAP
  }

  return result
}

export function boardNodeId(endpointId: number): string {
  return `ep-${endpointId}`
}

export function parseBoardNodeId(cellId: string): number | null {
  const m = /^ep-(\d+)$/.exec(cellId)
  return m ? Number(m[1]) : null
}
