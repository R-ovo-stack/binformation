import { http } from './http'
import type { FlowDetail, FlowSavePayload, FlowSummary } from '@/types/flow'

export async function listFlowsByAsset(assetId: number): Promise<FlowSummary[]> {
  const { data } = await http.get<FlowSummary[]>(`/api/assets/${assetId}/flows`)
  return data
}

export async function getFlow(flowId: number): Promise<FlowDetail> {
  const { data } = await http.get<FlowDetail>(`/api/flows/${flowId}`)
  return data
}

export async function createFlow(assetId: number, payload: FlowSavePayload): Promise<FlowDetail> {
  const { data } = await http.post<FlowDetail>(`/api/assets/${assetId}/flows`, payload)
  return data
}

export async function updateFlow(flowId: number, payload: FlowSavePayload): Promise<FlowDetail> {
  const { data } = await http.put<FlowDetail>(`/api/flows/${flowId}`, payload)
  return data
}

export async function deleteFlow(flowId: number): Promise<void> {
  await http.delete(`/api/flows/${flowId}`)
}
