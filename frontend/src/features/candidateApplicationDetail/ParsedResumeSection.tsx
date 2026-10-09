import { useId } from 'react'
import { AlertCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useResumeParsedDataQuery } from '../resumes/queries'
import { ResumeParsedDataSkeleton, ResumeParsedDataView } from '../resumes/ResumeParsedDataView'
import type { ApplicationCandidateDetail } from './types'

// Khoi "CV đã trích xuất" (R-T9..R-T11). Chi goi /parsed khi CV DONE. KHONG co nut thu lai/trich xuat lai
// (R-P4 - goi AI ton phi, thuoc trang "Hồ sơ và CV"); "Hồ sơ và CV" chi la lien ket chu. Cau trang thai mau
// on-surface, khong m3-error (trang thai xu ly, khong phai loi thao tac).
export function ParsedResumeSection({ resume }: { resume: ApplicationCandidateDetail['resume'] }) {
  const headingId = useId()
  const isDone = resume.parseStatus === 'DONE'
  const parsedQuery = useResumeParsedDataQuery(resume.id, isDone)

  let content
  if (resume.parseStatus === 'PENDING' || resume.parseStatus === 'PROCESSING') {
    content = (
      <p className="text-sm text-m3-on-surface">
        CV đang chờ trích xuất. Nội dung sẽ hiện khi hệ thống trích xuất xong.
      </p>
    )
  } else if (resume.parseStatus === 'FAILED') {
    content = (
      <div className="flex flex-col gap-1 text-sm text-m3-on-surface">
        <p>Trích xuất CV thất bại.</p>
        {resume.parseError && <p className="break-words">{resume.parseError}</p>}
        <p>
          Bạn có thể tải lên CV khác hoặc thử lại ở trang{' '}
          <Link to="/candidate/profile" className="font-medium text-m3-primary hover:underline">
            Hồ sơ và CV
          </Link>
          .
        </p>
      </div>
    )
  } else if (parsedQuery.isLoading) {
    content = <ResumeParsedDataSkeleton />
  } else if (parsedQuery.isError) {
    content = (
      <div className="flex flex-col items-start gap-2">
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          Không tải được dữ liệu, vui lòng thử lại.
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => parsedQuery.refetch()}>
          Thử lại
        </Button>
      </div>
    )
  } else if (!parsedQuery.data) {
    // /parsed tra 404 (getParsedResumeRequest doi thanh null) - cau co san cua ResumeParsedDataDialog.
    content = <p className="text-sm text-m3-on-surface">Chưa có dữ liệu trích xuất cho CV này.</p>
  } else {
    content = <ResumeParsedDataView data={parsedQuery.data} />
  }

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 py-6 first:pt-0 last:pb-0">
      <h2 id={headingId} className="text-m3-title-md text-m3-on-surface">
        CV đã trích xuất
      </h2>
      <div aria-live="polite">{content}</div>
    </section>
  )
}
