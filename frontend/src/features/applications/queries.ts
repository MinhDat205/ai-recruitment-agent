import { useMutation } from '@tanstack/react-query'
import { createApplicationRequest } from './api'

export function useCreateApplicationMutation() {
  return useMutation({
    mutationFn: createApplicationRequest,
  })
}
