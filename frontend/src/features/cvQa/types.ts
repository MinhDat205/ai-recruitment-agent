// FR-C08 - khop DTO backend (REQUIREMENT muc 4.5). Khong co cau hoi, van ban CV, model, prompt hay diem.

// Phia dang hoi - chon tien to API /hr/... hay /candidates/... va cau chu theo phia.
export type CvQaSide = 'hr' | 'candidate'

export type CvQaOutcome = 'ANSWERED' | 'NOT_FOUND' | 'REFUSED'

export type CvQaRefusalReason = 'PERSONAL_INFO' | 'HIRING_JUDGEMENT' | 'CV_IMPROVEMENT' | 'OUT_OF_SCOPE'

// Q1, Q2 - DUNG MOT field.
export interface CvQaRequest {
  question: string
}

export interface CvQaCitation {
  quote: string
}

// Q1, Q2
export interface CvQaResponse {
  outcome: CvQaOutcome
  // chi khi ANSWERED; null o hai truong hop con lai
  answer: string | null
  // 1-5 khi ANSWERED; [] o hai truong hop con lai
  citations: CvQaCitation[]
  // chi khi REFUSED; null o hai truong hop con lai
  refusalReason: CvQaRefusalReason | null
  // R-A3 - true khi CV bi cat phan giua (chi ANSWERED/NOT_FOUND)
  cvTruncated: boolean
}

// Mot luot hoi dap - chi nam trong state React (R-N1). id sinh o client, chi dung lam key khi render.
export interface CvQaTurn {
  id: string
  question: string
  response: CvQaResponse
}
