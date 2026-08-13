import { ElMessageBox } from 'element-plus'
import { analyzeImpact } from '@/api/impact'
import type { ImpactAnalysis, ImpactEntityType, ImpactGroup } from '@/types/impact'

function formatGroupLines(group: ImpactGroup, maxItems = 6): string[] {
  const lines = [`• ${group.message}`]
  for (const item of group.items.slice(0, maxItems)) {
    const extra = item.assetName ? `（${item.assetName}）` : ''
    lines.push(`  - ${item.label}${extra}`)
  }
  if (group.items.length > maxItems) {
    lines.push(`  … 另有 ${group.items.length - maxItems} 条`)
  }
  return lines
}

export function formatImpactMessage(analysis: ImpactAnalysis): string {
  const parts: string[] = [analysis.summary, '']
  if (analysis.blockers.length) {
    parts.push('阻塞项：')
    for (const group of analysis.blockers) {
      parts.push(...formatGroupLines(group))
    }
    parts.push('')
  }
  if (analysis.warnings.length) {
    parts.push('提示：')
    for (const group of analysis.warnings) {
      parts.push(...formatGroupLines(group))
    }
  }
  return parts.join('\n').trim()
}

export async function confirmImpactDelete(options: {
  entityType: ImpactEntityType
  entityId: number
  entityLabel: string
  title?: string
}): Promise<boolean> {
  const analysis = await analyzeImpact(options.entityType, options.entityId, 'DELETE')
  const message = formatImpactMessage(analysis)

  if (!analysis.canProceed) {
    await ElMessageBox.alert(message, '无法删除', {
      type: 'error',
      confirmButtonText: '知道了',
    })
    return false
  }

  try {
    await ElMessageBox.confirm(message, options.title ?? `删除「${options.entityLabel}」`, {
      type: analysis.warnings.length ? 'warning' : 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
    })
    return true
  } catch {
    return false
  }
}

export async function loadImpactUpdate(
  entityType: ImpactEntityType,
  entityId: number,
): Promise<ImpactAnalysis> {
  return analyzeImpact(entityType, entityId, 'UPDATE')
}
