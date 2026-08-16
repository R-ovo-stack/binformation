import { h } from 'vue'
import { ElMessageBox } from 'element-plus'
import { analyzeImpact } from '@/api/impact'
import ImpactDeleteBody from '@/components/ImpactDeleteBody.vue'
import router from '@/router'
import type { ImpactAnalysis, ImpactEntityType, ImpactGroup, ImpactItem } from '@/types/impact'
import { resolveImpactItemRoute } from '@/utils/impactNav'

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

function impactPageQuery(entityType: ImpactEntityType, entityId: number) {
  return {
    name: 'impact' as const,
    query: {
      type: entityType,
      id: String(entityId),
      action: 'DELETE',
    },
  }
}

function renderDeleteBody(
  analysis: ImpactAnalysis,
  entityType: ImpactEntityType,
  entityId: number,
) {
  return h(ImpactDeleteBody, {
    analysis,
    onOpenItem: (item: ImpactItem) => {
      const route = resolveImpactItemRoute(item)
      if (!route) return
      ElMessageBox.close()
      void router.push(route)
    },
    onOpenFull: () => {
      ElMessageBox.close()
      void router.push(impactPageQuery(entityType, entityId))
    },
  })
}

export async function confirmImpactDelete(options: {
  entityType: ImpactEntityType
  entityId: number
  entityLabel: string
  title?: string
}): Promise<boolean> {
  const analysis = await analyzeImpact(options.entityType, options.entityId, 'DELETE')
  const body = renderDeleteBody(analysis, options.entityType, options.entityId)

  if (!analysis.canProceed) {
    try {
      await ElMessageBox({
        title: '无法删除',
        type: 'error',
        message: body,
        confirmButtonText: '知道了',
        showCancelButton: true,
        cancelButtonText: '完整分析',
        distinguishCancelAndClose: true,
        customClass: 'impact-delete-box',
      })
    } catch (action) {
      if (action === 'cancel') {
        void router.push(impactPageQuery(options.entityType, options.entityId))
      }
    }
    return false
  }

  try {
    await ElMessageBox({
      title: options.title ?? `删除「${options.entityLabel}」`,
      type: 'warning',
      message: body,
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
      customClass: 'impact-delete-box',
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
