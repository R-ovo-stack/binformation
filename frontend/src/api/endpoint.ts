import { http } from './http'
import type { EndpointDetail, EndpointSavePayload, EndpointTypeMeta } from '@/types/endpoint'
import type { EndpointImportResult } from '@/types/endpointImport'

function parseFilename(contentDisposition: string | undefined, fallback: string): string {
  if (!contentDisposition) return fallback
  const match = /filename="([^"]+)"/i.exec(contentDisposition)
  return match?.[1] ?? fallback
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export async function downloadEndpointImportTemplate(): Promise<void> {
  const res = await http.get('/api/endpoints/import/template', { responseType: 'blob' })
  const filename = parseFilename(res.headers['content-disposition'], 'endpoint-import-template.csv')
  triggerDownload(res.data as Blob, filename)
}

export async function importEndpointsFromCsv(file: File): Promise<EndpointImportResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<EndpointImportResult>('/api/endpoints/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

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
