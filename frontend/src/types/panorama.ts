export interface PanoramaAssetNode {
  assetId: number
  name: string
  code: string
  dataType: string
  status: string
  primaryFlowCount: number
  derivationInCount: number
  derivationOutCount: number
}

export type PanoramaEdgeType = 'DERIVE' | 'ENDPOINT_LINK'

export interface PanoramaEdge {
  id: string
  sourceAssetId: number
  targetAssetId: number
  type: PanoramaEdgeType | string
  label: string
  derivationId?: number | null
  endpointId?: number | null
  endpointLabel?: string | null
}

export interface PanoramaGraph {
  nodes: PanoramaAssetNode[]
  edges: PanoramaEdge[]
  assetCount: number
  edgeCount: number
}

export interface PositionedPanoramaNode extends PanoramaAssetNode {
  x: number
  y: number
  width: number
  height: number
}

export function panoramaEdgeStroke(type: string): string {
  return type === 'DERIVE' ? '#0f766e' : '#1d4ed8'
}

export function dataTypeLabel(type: string): string {
  return type === 'KAFKA_MESSAGE' ? 'Kafka消息' : type === 'FILE' ? '文件' : type
}
