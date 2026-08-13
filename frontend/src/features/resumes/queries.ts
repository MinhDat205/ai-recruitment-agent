import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listResumesRequest, setPrimaryResumeRequest, uploadResumeRequest } from './api'

const RESUMES_QUERY_KEY = ['resumes', 'mine']

export function useResumesQuery() {
  return useQuery({
    queryKey: RESUMES_QUERY_KEY,
    queryFn: listResumesRequest,
  })
}

export function useUploadResumeMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, versionLabel }: { file: File; versionLabel?: string }) =>
      uploadResumeRequest(file, versionLabel),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RESUMES_QUERY_KEY })
    },
  })
}

export function useSetPrimaryResumeMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => setPrimaryResumeRequest(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RESUMES_QUERY_KEY })
    },
  })
}
