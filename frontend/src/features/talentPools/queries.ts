import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createTalentPoolRequest,
  deleteTalentPoolRequest,
  getTalentPoolEntriesRequest,
  getTalentPoolRequest,
  getTalentPoolsRequest,
  removeTalentPoolEntryRequest,
  renameTalentPoolRequest,
} from './api'

// UI.md muc 5a - moi khoa cua kho bat dau bang 'talentPools' de mot lan invalidate tien to lam moi tat ca (danh sach,
// chi tiet, trang muc).
export const TALENT_POOLS_KEY = ['talentPools'] as const

export function talentPoolKey(poolId: string) {
  return ['talentPools', poolId] as const
}

export function talentPoolEntriesKey(poolId: string, page: number) {
  return ['talentPools', poolId, 'entries', page] as const
}

export function useTalentPoolsQuery() {
  return useQuery({ queryKey: TALENT_POOLS_KEY, queryFn: getTalentPoolsRequest })
}

export function useTalentPoolQuery(poolId: string) {
  return useQuery({ queryKey: talentPoolKey(poolId), queryFn: () => getTalentPoolRequest(poolId) })
}

// keepPreviousData: doi trang khong nhay ve skeleton.
export function useTalentPoolEntriesQuery(poolId: string, page: number) {
  return useQuery({
    queryKey: talentPoolEntriesKey(poolId, page),
    queryFn: () => getTalentPoolEntriesRequest(poolId, page),
    placeholderData: keepPreviousData,
  })
}

// Moi mutation lam moi tien to 'talentPools' va hop thoai cua don (contains co the doi).
function useInvalidateAll() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: TALENT_POOLS_KEY }),
      queryClient.invalidateQueries({ queryKey: ['applicationTalentPools'] }),
    ])
}

export function useCreateTalentPoolMutation() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: (name: string) => createTalentPoolRequest(name), onSuccess: invalidate })
}

export function useRenameTalentPoolMutation(poolId: string) {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: (name: string) => renameTalentPoolRequest(poolId, name), onSuccess: invalidate })
}

// Khong invalidate o day: trang chi tiet dieu huong ve danh sach truoc, roi moi lam moi (tranh tai lai kho vua xoa -> 404).
export function useDeleteTalentPoolMutation(poolId: string) {
  return useMutation({ mutationFn: () => deleteTalentPoolRequest(poolId) })
}

export function useRemoveTalentPoolEntryMutation(poolId: string) {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: (applicationId: string) => removeTalentPoolEntryRequest(poolId, applicationId),
    onSuccess: invalidate,
  })
}
