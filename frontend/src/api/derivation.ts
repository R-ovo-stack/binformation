import { http } from './http'
import type { DerivationDetail, DerivationSavePayload } from '@/types/derivation'

export async function listDerivationsByAsset(assetId: number): Promise<DerivationDetail[]> {
  const { data } = await http.get<DerivationDetail[]>(`/api/assets/${assetId}/derivations`)
  return data
}

export async function getDerivation(id: number): Promise<DerivationDetail> {
  const { data } = await http.get<DerivationDetail>(`/api/derivations/${id}`)
  return data
}

export async function createDerivation(
  assetId: number,
  payload: DerivationSavePayload,
): Promise<DerivationDetail> {
  const { data } = await http.post<DerivationDetail>(`/api/assets/${assetId}/derivations`, payload)
  return data
}

export async function updateDerivation(
  id: number,
  payload: DerivationSavePayload,
): Promise<DerivationDetail> {
  const { data } = await http.put<DerivationDetail>(`/api/derivations/${id}`, payload)
  return data
}

export async function deleteDerivation(id: number): Promise<void> {
  await http.delete(`/api/derivations/${id}`)
}
