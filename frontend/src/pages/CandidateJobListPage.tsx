import { CandidateLayout } from '../components/layout/CandidateLayout'
import { JobBoard } from '../features/jobs/JobBoard'

// Noi dung "Viec lam" cho candidate da dang nhap - R-L3: dung chung JobBoard voi
// PublicJobListPage.tsx, chi khac layout boc ngoai (CandidateLayout thay vi PublicLayout de giu
// header/nav tai khoan). FR-U15 R-L4: truyen showRecommendations - PublicJobListPage KHONG truyen
// (giu mac dinh false), chi trang ung vien da dang nhap moi co khoi "Goi y cho ban". FR-U13: tuong tu,
// chi trang nay truyen naturalLanguageSearch (che do "Tim bang mo ta").
export function CandidateJobListPage() {
  return (
    <CandidateLayout>
      <JobBoard showRecommendations naturalLanguageSearch />
    </CandidateLayout>
  )
}
