// FR-C09 REQUIREMENT muc 4.4 - kieu phia UNG VIEN. KHONG co decidedBy, id nguoi HR, identityMasked, diem, rubric (R-C3).

// Trang thai cua YEU CAU (bang rieng), khong phai trang thai don - don van dung 5 gia tri ApplicationStatus.
export type ReconsiderationStatus = 'OPEN' | 'ACCEPTED' | 'DECLINED'

// R-C1 - dung 6 gia tri do backend tinh (ke ca AVAILABLE/EXPIRED theo now() cua DB, R-T4); frontend chi hien thi.
export type CandidateReconsiderationState = 'AVAILABLE' | 'EXPIRED' | 'JOB_REMOVED' | ReconsiderationStatus

// FR-C09 phia HR (dot 6) - khoa reconsideration o E1 cua FR-H09, phan tu cua H1, than response H2. Don dang an danh:
// candidateName la nhan "Ứng viên #…", reason/hrResponse da che o backend (R-A2). Khong co decidedBy.
export interface HrReconsideration {
  applicationId: string
  candidateName: string
  identityMasked: boolean
  appliedAt: string
  rejectedAt: string | null
  requestedAt: string
  reason: string | null
  status: ReconsiderationStatus
  decidedAt: string | null
  hrResponse: string | null
}

// H1 - openCount dem yeu cau OPEN cua tin, khong phu thuoc bo loc.
export interface HrReconsiderationList {
  jobId: string
  openCount: number
  items: HrReconsideration[]
}

// Yeu cau dang duoc HR thao tac (ten hoac nhan "Ứng viên #…" khi an danh) - dau vao cua hai hop thoai.
export interface ReconsiderationTarget {
  applicationId: string
  candidateName: string
}

// Bo loc H1 (tham so status cua backend).
export type HrReconsiderationFilter = 'OPEN' | 'ALL'

// Khoa reconsideration cua E1 ung vien va than response C1.
export interface CandidateReconsideration {
  state: CandidateReconsiderationState
  deadline: string
  requestedAt: string | null
  reason: string | null
  decidedAt: string | null
  hrResponse: string | null
}
