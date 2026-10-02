import { CandidateLayout } from '../components/layout/CandidateLayout'
import { JobBoard } from '../features/jobs/JobBoard'

// Noi dung "Viec lam" cho candidate da dang nhap - R-L3: dung chung JobBoard voi
// PublicJobListPage.tsx, chi khac layout boc ngoai (CandidateLayout thay vi PublicLayout de giu
// header/nav tai khoan).
export function CandidateJobListPage() {
  return (
    <CandidateLayout>
      <JobBoard />
    </CandidateLayout>
  )
}
