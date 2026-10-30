// FR-C09 REQUIREMENT muc 4.4 - kieu phia UNG VIEN. KHONG co decidedBy, id nguoi HR, identityMasked, diem, rubric (R-C3).

// Trang thai cua YEU CAU (bang rieng), khong phai trang thai don - don van dung 5 gia tri ApplicationStatus.
export type ReconsiderationStatus = 'OPEN' | 'ACCEPTED' | 'DECLINED'

// R-C1 - dung 6 gia tri do backend tinh (ke ca AVAILABLE/EXPIRED theo now() cua DB, R-T4); frontend chi hien thi.
export type CandidateReconsiderationState = 'AVAILABLE' | 'EXPIRED' | 'JOB_REMOVED' | ReconsiderationStatus

// Khoa reconsideration cua E1 ung vien va than response C1.
export interface CandidateReconsideration {
  state: CandidateReconsiderationState
  deadline: string
  requestedAt: string | null
  reason: string | null
  decidedAt: string | null
  hrResponse: string | null
}
