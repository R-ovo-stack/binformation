import { http } from './http'
import type { EndpointOption, ExecutorOption } from '@/types/flow'

export async function listEndpointOptions(type?: string): Promise<EndpointOption[]> {
  const { data } = await http.get<EndpointOption[]>('/api/endpoints', {
    params: { optionsOnly: true, type },
  })
  return data
}

export async function listExecutorOptions(): Promise<ExecutorOption[]> {
  const { data } = await http.get<ExecutorOption[]>('/api/executors')
  return data
}
