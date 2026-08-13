import type { VisualEditSnapshot } from '@/utils/visualEditHistory'

const PREFIX = 'binformation:flow-visual:'

export interface StoredVisualDraft {
  assetId: number
  flowId?: string
  savedAt: string
  snapshot: VisualEditSnapshot
}

function storageKey(assetId: number) {
  return `${PREFIX}${assetId}`
}

export function loadLocalVisualDraft(assetId: number): StoredVisualDraft | null {
  if (typeof localStorage === 'undefined') return null
  try {
    const raw = localStorage.getItem(storageKey(assetId))
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredVisualDraft
    if (parsed.assetId !== assetId || !parsed.snapshot) return null
    return parsed
  } catch {
    return null
  }
}

export function saveLocalVisualDraft(data: StoredVisualDraft) {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(storageKey(data.assetId), JSON.stringify(data))
  } catch {
    // ignore quota / private mode
  }
}

export function clearLocalVisualDraft(assetId: number) {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.removeItem(storageKey(assetId))
  } catch {
    // ignore
  }
}

export function formatDraftSavedAt(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
