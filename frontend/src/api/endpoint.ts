import { http } from './http'
import type { EndpointDetail, EndpointSavePayload, EndpointTypeMeta } from '@/types/endpoint'

export async function listEndpoints(type?: string, parentId?: number): Promise<EndpointDetail[]> {
  const { data } = await http.get<EndpointDetail[]>('/api/endpoints', {
    params: { type, parentId },
  })
  return data
}

export async function getEndpoint(id: number): Promise<EndpointDetail> {
  const { data } = await http.get<EndpointDetail>(`/api/endpoints/${id}`)
  return data
}

export async function createEndpoint(payload: EndpointSavePayload): Promise<EndpointDetail> {
  const { data } = await http.post<EndpointDetail>('/api/endpoints', payload)
  return data
}

export async function updateEndpoint(id: number, payload: EndpointSavePayload): Promise<EndpointDetail> {
  const { data } = await http.put<EndpointDetail>(`/api/endpoints/${id}`, payload)
  return data
}

export async function deleteEndpoint(id: number): Promise<void> {
  await http.delete(`/api/endpoints/${id}`)
}

export async function getEndpointTypeMeta(): Promise<EndpointTypeMeta> {
  const { data } = await http.get<EndpointTypeMeta>('/api/endpoints/meta/types')
  return data
}

export async function getEndpointParentTypes(): Promise<Record<string, string[]>> {
  const { data } = await http.get<Record<string, string[]>>('/api/endpoints/meta/parent-types')
  return data
}
