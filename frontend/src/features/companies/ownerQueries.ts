import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { createCompanyRequest, getMyCompanyRequest, updateCompanyRequest, uploadLogoRequest } from './ownerApi'
import type { CompanyOwnerRequest } from './ownerTypes'

const MY_COMPANY_QUERY_KEY = ['hr-company', 'me']

export function useMyCompanyQuery() {
  return useQuery({
    queryKey: MY_COMPANY_QUERY_KEY,
    queryFn: getMyCompanyRequest,
    // 404 nghia la HR chua tao cong ty - trang thai binh thuong, khong phai loi tam thoi nen
    // khong retry (mac dinh queryClient se retry 1 lan cho cac loi khac, van giu nguyen).
    retry: (failureCount, error) => {
      if (isAxiosError(error) && error.response?.status === 404) {
        return false
      }
      return failureCount < 1
    },
  })
}

export function useSaveCompanyMutation(existingId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CompanyOwnerRequest) =>
      existingId ? updateCompanyRequest(existingId, payload) : createCompanyRequest(payload),
    onSuccess: (data) => {
      queryClient.setQueryData(MY_COMPANY_QUERY_KEY, data)
    },
  })
}

export function useUploadLogoMutation(companyId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      if (!companyId) {
        return Promise.reject(new Error('Chua co cong ty de upload logo'))
      }
      return uploadLogoRequest(companyId, file)
    },
    onSuccess: (data) => {
      queryClient.setQueryData(MY_COMPANY_QUERY_KEY, data)
    },
  })
}
