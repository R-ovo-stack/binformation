import { http } from '@/api/http'
import type { SearchResult } from '@/types/search'

export async function searchLedger(query: string, limit = 8): Promise<SearchResult> {
  const { data } = await http.get<SearchResult>('/api/search', {
    params: { q: query, limit },
  })
  return data
}
