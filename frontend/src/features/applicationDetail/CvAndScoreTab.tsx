import { useState } from 'react'
import { AlertCircle, Download, FileText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { extractErrorMessage } from '../../lib/httpError'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { ResumeParsedDataSkeleton, ResumeParsedDataView } from '../resumes/ResumeParsedDataView'
import { downloadApplicationResume } from '../scoring/downloadApplicationResume'
import { CoverLetterBlock } from './CoverLetterBlock'
import { useApplicationParsedResumeQuery } from './queries'
import { ScoreSection, TabLoadError } from './ScoreSection'
import type { ApplicationHrDetail } from './types'

// Khoi 1 "Hồ sơ đã nộp": file goc + thu gioi thieu. "Xem CV gốc" LUON bam duoc o trang nay, bat ke
// resumeParseStatus (R-V1, R-V2) - khac danh sach theo Job (van khoa khi CV chua DONE, R-P4). Chi tam
// khoa trong luc dang tai de tranh bam hai lan, giong danh sach.
function SubmittedResumeSection({ detail }: { detail: ApplicationHrDetail }) {
  const [isDownloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  async function handleDownload() {
    setDownloadError(null)
    setDownloading(true)
    try {
      await downloadApplicationResume(detail.id)
    } catch (err) {
      setDownloadError(extractErrorMessage(err, 'Tải CV gốc thất bại, vui lòng thử lại.'))
    } finally {
      setDownloading(false)
    }
  }

  // R-T3b - null hoac rong thi KHONG hien tieu de "Thư giới thiệu". Khong trim (R-D8).
  const hasCoverLetter = detail.coverLetter !== null && detail.coverLetter !== ''

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-m3-title-md text-m3-on-surface">Hồ sơ đã nộp</h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-full sm:w-auto"
          disabled={isDownloading}
          onClick={handleDownload}
        >
          <Download className="h-3.5 w-3.5" aria-hidden="true" />
          Xem CV gốc
        </Button>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm text-m3-on-surface">
        <FileText className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
        <span className="break-all">{detail.resumeFileName}</span>
        <ParseStatusBadge status={detail.resumeParseStatus} />
      </div>
      {downloadError && (
        <div role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          <p>{downloadError}</p>
        </div>
      )}
      {hasCoverLetter && <CoverLetterBlock text={detail.coverLetter as string} />}
    </section>
  )
}

// Khoi 3 "CV đã trích xuất" (R-T1..R-T3). Chi goi E2 khi CV DONE; PENDING/PROCESSING/FAILED hien cau
// trang thai, mau on-surface (khong dung m3-error - day la trang thai xu ly, UI.md muc 10).
function ParsedResumeSection({ detail }: { detail: ApplicationHrDetail }) {
  const isDone = detail.resumeParseStatus === 'DONE'
  const parsedQuery = useApplicationParsedResumeQuery(detail.id, isDone)

  let content
  if (detail.resumeParseStatus === 'PENDING' || detail.resumeParseStatus === 'PROCESSING') {
    content = (
      <p className="text-sm text-m3-on-surface">CV đang chờ trích xuất. Nội dung sẽ hiện khi hệ thống trích xuất xong.</p>
    )
  } else if (detail.resumeParseStatus === 'FAILED') {
    content = (
      <div className="flex flex-col gap-1 text-sm text-m3-on-surface">
        <p>Trích xuất CV thất bại.</p>
        {detail.resumeParseError && <p className="break-words">{detail.resumeParseError}</p>}
      </div>
    )
  } else if (parsedQuery.isLoading) {
    content = <ResumeParsedDataSkeleton />
  } else if (!parsedQuery.data) {
    content = <TabLoadError onRetry={() => parsedQuery.refetch()} />
  } else {
    content = <ResumeParsedDataView data={parsedQuery.data} />
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-m3-title-md text-m3-on-surface">CV đã trích xuất</h2>
      {content}
    </section>
  )
}

// Tab "CV & điểm" - ba khoi theo thu tu CO DINH (UI.md muc 4a): Hồ sơ đã nộp -> Điểm theo rubric ->
// CV đã trích xuất (khoi dai nhat dat cuoi de diem va evidence khong bi day xuong).
export function CvAndScoreTab({ detail }: { detail: ApplicationHrDetail }) {
  return (
    <div className="flex flex-col divide-y divide-m3-outline-variant">
      <div className="pb-6">
        <SubmittedResumeSection detail={detail} />
      </div>
      <div className="py-6">
        <ScoreSection applicationId={detail.id} jobId={detail.jobId} resumeParseStatus={detail.resumeParseStatus} />
      </div>
      <div className="pt-6">
        <ParsedResumeSection detail={detail} />
      </div>
    </div>
  )
}
