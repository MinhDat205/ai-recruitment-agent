import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  changeHrJobStatusRequest,
  createHrJobRequest,
  deleteHrJobRequest,
  getHrJobRequest,
  getInterviewTemplateRequest,
  listHrJobsRequest,
  updateHrJobRequest,
  updateInterviewTemplateRequest,
} from './ownerApi'
import type {
  HrJobListParams,
  InterviewTemplateOwnerRequest,
  JobCreateOwnerRequest,
  JobOwnerRequest,
  JobStatus,
} from './ownerTypes'

const HR_JOBS_KEY = ['hr-jobs']

function hrJobsListKey(params: HrJobListParams) {
  return [...HR_JOBS_KEY, 'list', params]
}

function hrJobDetailKey(id: string | undefined) {
  return [...HR_JOBS_KEY, 'detail', id]
}

function interviewTemplateKey(jobId: string | undefined) {
  return ['hr-interview-template', jobId]
}

export function useHrJobsQuery(params: HrJobListParams) {
  return useQuery({
    queryKey: hrJobsListKey(params),
    queryFn: () => listHrJobsRequest(params),
    placeholderData: keepPreviousData,
  })
}

export function useHrJobQuery(id: string | undefined) {
  return useQuery({
    queryKey: hrJobDetailKey(id),
    queryFn: () => getHrJobRequest(id as string),
    enabled: Boolean(id),
  })
}

export function useCreateHrJobMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: JobCreateOwnerRequest) => createHrJobRequest(payload),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: HR_JOBS_KEY })
      queryClient.setQueryData(hrJobDetailKey(data.id), data)
    },
  })
}

export function useUpdateHrJobMutation(id: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: JobOwnerRequest) => updateHrJobRequest(id as string, payload),
    onSuccess: (data) => {
      queryClient.setQueryData(hrJobDetailKey(id), data)
      queryClient.invalidateQueries({ queryKey: HR_JOBS_KEY })
    },
  })
}

export function useChangeHrJobStatusMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: JobStatus }) => changeHrJobStatusRequest(id, status),
    onSuccess: (data) => {
      queryClient.setQueryData(hrJobDetailKey(data.id), data)
      queryClient.invalidateQueries({ queryKey: HR_JOBS_KEY })
    },
  })
}

export function useDeleteHrJobMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteHrJobRequest(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: HR_JOBS_KEY })
    },
  })
}

export function useInterviewTemplateQuery(jobId: string | undefined) {
  return useQuery({
    queryKey: interviewTemplateKey(jobId),
    queryFn: () => getInterviewTemplateRequest(jobId as string),
    enabled: Boolean(jobId),
  })
}

export function useUpdateInterviewTemplateMutation(jobId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: InterviewTemplateOwnerRequest) => updateInterviewTemplateRequest(jobId as string, payload),
    onSuccess: (data) => {
      queryClient.setQueryData(interviewTemplateKey(jobId), data)
    },
  })
}
