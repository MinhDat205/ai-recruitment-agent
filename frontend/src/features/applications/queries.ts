import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { candidateApplicationDetailKey } from '../candidateApplicationDetail/queries'
import {
  createApplicationRequest,
  getApplicationHistoryRequest,
  getMyApplicationsRequest,
  withdrawApplicationRequest,
} from './api'

const MY_APPLICATIONS_QUERY_KEY = ['my-applications']

function applicationHistoryKey(id: string | undefined) {
  return ['application-history', id]
}

export function useCreateApplicationMutation() {
  return useMutation({
    mutationFn: createApplicationRequest,
  })
}

export function useMyApplicationsQuery() {
  return useQuery({
    queryKey: MY_APPLICATIONS_QUERY_KEY,
    queryFn: getMyApplicationsRequest,
  })
}

export function useApplicationHistoryQuery(id: string | undefined) {
  return useQuery({
    queryKey: applicationHistoryKey(id),
    queryFn: () => getApplicationHistoryRequest(id as string),
    enabled: Boolean(id),
  })
}

// FR-U08 R-W3, R-W4 - mutation rut don DUY NHAT. Thanh cong: lam moi danh sach don, lich su cua don va
// chi tiet don (E1) de badge/nut/tab Lich su doi ngay. 409 APPLICATION_NOT_WITHDRAWABLE (HR vua doi trang
// thai o noi khac): lam moi E1 de nut "Rut don" bien mat neu trang thai da doi.
export function useWithdrawApplicationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: withdrawApplicationRequest,
    onSuccess: (_data, applicationId) => {
      queryClient.invalidateQueries({ queryKey: MY_APPLICATIONS_QUERY_KEY })
      queryClient.invalidateQueries({ queryKey: applicationHistoryKey(applicationId) })
      queryClient.invalidateQueries({ queryKey: candidateApplicationDetailKey(applicationId) })
    },
    onError: (error, applicationId) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        queryClient.invalidateQueries({ queryKey: candidateApplicationDetailKey(applicationId) })
      }
    },
  })
}
