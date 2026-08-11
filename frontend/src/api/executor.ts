import { http } from './http'
import type { ExecutorDetail, ExecutorSavePayload } from '@/types/executor'

export async function listExecutors(): Promise<ExecutorDetail[]> {
  const { data } = await http.get<ExecutorDetail[]>('/api/executors')
  return data
}

export async function getExecutor(id: number): Promise<ExecutorDetail> {
  const { data } = await http.get<ExecutorDetail>(`/api/executors/${id}`)
  return data
}

export async function createExecutor(payload: ExecutorSavePayload): Promise<ExecutorDetail> {
  const { data } = await http.post<ExecutorDetail>('/api/executors', payload)
  return data
}

export async function updateExecutor(id: number, payload: ExecutorSavePayload): Promise<ExecutorDetail> {
  const { data } = await http.put<ExecutorDetail>(`/api/executors/${id}`, payload)
  return data
}

export async function deleteExecutor(id: number): Promise<void> {
  await http.delete(`/api/executors/${id}`)
}
