import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addRubricCriterionRequest,
  deleteRubricCriterionRequest,
  getRubricRequest,
  updateRubricCriterionRequest,
} from './ownerApi'
import type { RubricCriterionRequest, RubricCriterionResponse } from './ownerTypes'

function rubricKey(jobId: string | undefined) {
  return ['hr-rubric', jobId]
}

export function useRubricQuery(jobId: string | undefined) {
  return useQuery({
    queryKey: rubricKey(jobId),
    queryFn: () => getRubricRequest(jobId as string),
    enabled: Boolean(jobId),
  })
}

export function useAddRubricCriterionMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: RubricCriterionRequest) => addRubricCriterionRequest(jobId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: rubricKey(jobId) })
    },
  })
}

export function useUpdateRubricCriterionMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ criterionId, payload }: { criterionId: string; payload: RubricCriterionRequest }) =>
      updateRubricCriterionRequest(jobId, criterionId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: rubricKey(jobId) })
    },
  })
}

export function useDeleteRubricCriterionMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (criterionId: string) => deleteRubricCriterionRequest(jobId, criterionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: rubricKey(jobId) })
    },
  })
}

function toReorderPayload(criterion: RubricCriterionResponse, displayOrder: number): RubricCriterionRequest {
  return {
    name: criterion.name,
    description: criterion.description ?? undefined,
    scaleDescription: criterion.scaleDescription ?? undefined,
    weight: criterion.weight,
    maxScore: criterion.maxScore,
    displayOrder,
  }
}

// Khong co endpoint sap xep hang loat o backend (chi POST/PUT/DELETE tung tieu chi) - keo tha
// goi PUT cho tung tieu chi co display_order thay doi, giu nguyen moi truong khac (weight khong
// doi nen khong bao gio cham nguong RUBRIC_WEIGHT_EXCEEDED du goi song song).
export function useReorderRubricCriteriaMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (orderedCriteria: RubricCriterionResponse[]) => {
      const changed = orderedCriteria.filter((criterion, index) => criterion.displayOrder !== index)
      await Promise.all(
        changed.map((criterion) =>
          updateRubricCriterionRequest(
            jobId,
            criterion.id,
            toReorderPayload(criterion, orderedCriteria.indexOf(criterion)),
          ),
        ),
      )
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: rubricKey(jobId) })
    },
  })
}
