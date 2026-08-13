import type { FlowDetail } from '@/types/flow'

export interface VisualEditSnapshot {
  draft: FlowDetail | null
  editing: FlowDetail | null
  flowDetailsCache: Record<number, FlowDetail>
  canvasEndpointIds: number[]
  selectedEdgeId: string | null
  selectedPathIndex: number
}

export function cloneVisualEditSnapshot(snapshot: VisualEditSnapshot): VisualEditSnapshot {
  return JSON.parse(JSON.stringify(snapshot)) as VisualEditSnapshot
}

export class VisualEditHistory {
  private undoStack: VisualEditSnapshot[] = []
  private redoStack: VisualEditSnapshot[] = []
  private lastCommitted: VisualEditSnapshot | null = null
  private readonly maxSize: number

  constructor(maxSize = 50) {
    this.maxSize = maxSize
  }

  reset(snapshot: VisualEditSnapshot) {
    this.undoStack = []
    this.redoStack = []
    this.lastCommitted = cloneVisualEditSnapshot(snapshot)
  }

  push(current: VisualEditSnapshot) {
    const next = cloneVisualEditSnapshot(current)
    if (this.lastCommitted && JSON.stringify(next) === JSON.stringify(this.lastCommitted)) {
      return
    }
    if (this.lastCommitted) {
      this.undoStack.push(this.lastCommitted)
      if (this.undoStack.length > this.maxSize) {
        this.undoStack.shift()
      }
    }
    this.redoStack = []
    this.lastCommitted = next
  }

  /** Record a discrete mutation as before → after (always creates an undo step). */
  recordMutation(before: VisualEditSnapshot, after: VisualEditSnapshot) {
    const prev = cloneVisualEditSnapshot(before)
    const next = cloneVisualEditSnapshot(after)
    if (JSON.stringify(prev) === JSON.stringify(next)) return
    this.undoStack.push(prev)
    if (this.undoStack.length > this.maxSize) {
      this.undoStack.shift()
    }
    this.redoStack = []
    this.lastCommitted = next
  }

  canUndo() {
    return this.undoStack.length > 0
  }

  canRedo() {
    return this.redoStack.length > 0
  }

  undo(current: VisualEditSnapshot): VisualEditSnapshot | null {
    if (!this.undoStack.length) return null
    this.redoStack.push(cloneVisualEditSnapshot(current))
    const previous = this.undoStack.pop()
    if (!previous) return null
    this.lastCommitted = cloneVisualEditSnapshot(previous)
    return previous
  }

  redo(current: VisualEditSnapshot): VisualEditSnapshot | null {
    if (!this.redoStack.length) return null
    this.undoStack.push(cloneVisualEditSnapshot(current))
    const next = this.redoStack.pop()
    if (!next) return null
    this.lastCommitted = cloneVisualEditSnapshot(next)
    return next
  }
}
