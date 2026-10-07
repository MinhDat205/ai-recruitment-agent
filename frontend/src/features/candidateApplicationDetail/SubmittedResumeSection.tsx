import { useId, useState } from 'react'
import { AlertCircle, Download, FileText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CoverLetterBlock } from '../applicationDetail/CoverLetterBlock'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { downloadResumeRequest } from '../resumes/api'
import type { ApplicationCandidateDetail } from './types'

// Khoi "Hồ sơ đã nộp": tep CV DA NOP (resume.id = job_applications.resume_id, R-D2) + thu gioi thieu.
// Tai file goc bang endpoint co san cua ung vien (/api/candidates/resumes/{id}/download, R-Q5), blob qua
// axios vi endpoint can header Authorization (cung cach ResumeList.handleDownload). Loi tai (vd tep khong
// con trong kho -> 404 RESUME_NOT_FOUND) chi hien cau duoi nut, khong loi trang (R-T13).
export function SubmittedResumeSection({
  resume,
  coverLetter,
}: {
  resume: ApplicationCandidateDetail['resume']
  coverLetter: string | null
}) {
  const headingId = useId()
  const errorId = useId()
  const [isDownloading, setDownloading] = useState(false)
  const [downloadFailed, setDownloadFailed] = useState(false)

  async function handleDownload() {
    setDownloadFailed(false)
    setDownloading(true)
    try {
      const blob = await downloadResumeRequest(resume.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = resume.fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch {
      setDownloadFailed(true)
    } finally {
      setDownloading(false)
    }
  }

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 py-6 first:pt-0 last:pb-0">
      <h2 id={headingId} className="text-m3-title-md text-m3-on-surface">
        Hồ sơ đã nộp
      </h2>
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <div className="flex flex-col items-start gap-2 text-sm text-m3-on-surface sm:flex-row sm:items-center">
          <span className="flex items-center gap-2">
            <FileText className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
            <span className="break-all">{resume.fileName}</span>
          </span>
          <ParseStatusBadge status={resume.parseStatus} />
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-full sm:w-auto"
          disabled={isDownloading}
          onClick={handleDownload}
          aria-describedby={downloadFailed ? errorId : undefined}
        >
          <Download className="h-3.5 w-3.5" aria-hidden="true" />
          {isDownloading ? 'Đang tải…' : 'Tải CV gốc'}
        </Button>
      </div>
      {downloadFailed && (
        <div id={errorId} role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          <p>Tải CV gốc thất bại, vui lòng thử lại.</p>
        </div>
      )}
      {/* R-T12 - null hoac rong thi KHONG hien tieu de "Thư giới thiệu". Khong trim (R-D7). */}
      {coverLetter !== null && coverLetter !== '' && <CoverLetterBlock text={coverLetter} />}
    </section>
  )
}
