// Khop ScaleLevelDescription o backend (com.recruitment.rubric.ScaleLevelDescription) - moi
// phan tu la mo ta HR viet cho MOT muc diem cua tieu chi, khong phai JSON tu do.
export interface ScaleLevelDescription {
  level: number
  description: string
}

export interface RubricCriterionResponse {
  id: string
  rubricId: string
  name: string
  description: string | null
  scaleDescription: ScaleLevelDescription[] | null
  weight: number
  maxScore: number
  displayOrder: number
  createdAt: string
  updatedAt: string
}

export interface RubricResponse {
  id: string
  jobId: string
  name: string | null
  locked: boolean
  totalWeight: number
  criteria: RubricCriterionResponse[]
}

// Khop RubricCriterionRequest o backend. maxScore/displayOrder de undefined duoc - backend tu
// ap dung default (5 va 0) khi thieu, giong cach JobOwnerRequest xu ly salaryCurrency.
export interface RubricCriterionRequest {
  name: string
  description?: string
  scaleDescription?: ScaleLevelDescription[]
  weight: number
  maxScore?: number
  displayOrder?: number
}
