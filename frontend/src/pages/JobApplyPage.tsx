import { Link, useParams } from 'react-router-dom'
import { PublicLayout } from '../components/layout/PublicLayout'
import { JobApplyForm } from '../features/applications/JobApplyForm'
import { useJobDetailQuery } from '../features/jobs/queries'

export function JobApplyPage() {
  const { id } = useParams<{ id: string }>()
  const { data: job, isLoading, isError } = useJobDetailQuery(id)

  if (isLoading) {
    return (
      <PublicLayout>
        <div className="mx-auto max-w-[720px] px-4 py-8 md:px-6">
          <div className="h-48 animate-pulse rounded-(--radius-card) bg-m3-surface-container" />
        </div>
      </PublicLayout>
    )
  }

  if (isError || !job || !id) {
    return (
      <PublicLayout>
        <div className="mx-auto max-w-[720px] px-4 py-16 text-center md:px-6">
          <p className="text-sm text-m3-on-surface">Không tìm thấy tin tuyển dụng.</p>
          <Link to="/" className="mt-3 inline-block text-sm text-m3-on-primary-container hover:underline">
            Về trang danh sách việc làm
          </Link>
        </div>
      </PublicLayout>
    )
  }

  return (
    <PublicLayout>
      <div className="mx-auto flex max-w-[720px] flex-col gap-6 px-4 py-8 md:px-6">
        <div>
          <p className="text-sm text-m3-on-surface">Ứng tuyển vị trí</p>
          <h1 className="text-2xl font-semibold text-m3-on-surface">{job.title}</h1>
        </div>
        <JobApplyForm jobId={id} />
      </div>
    </PublicLayout>
  )
}
