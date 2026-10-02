import { PublicLayout } from '../components/layout/PublicLayout'
import { JobBoard } from '../features/jobs/JobBoard'

export function PublicJobListPage() {
  return (
    <PublicLayout>
      <JobBoard />
    </PublicLayout>
  )
}
