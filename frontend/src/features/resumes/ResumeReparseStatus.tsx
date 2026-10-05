import { AlertCircle } from 'lucide-react'
import { LinearProgress } from './LinearProgress'
import { isReparseActive } from './queries'
import { canReparse, REPARSE_TEXT, reparseFailureText } from './resumeReparse'
import type { Resume } from './types'

interface ResumeReparseStatusProps {
  resume: Resume
  // Yeu cau trich xuat lai cua CV nay vua chuyen tu PENDING/RUNNING sang DONE trong phien nay.
  justCompleted: boolean
  // Loi khi GUI yeu cau (409/429...), thong diep backend.
  requestError: string | null
}

// Phan trich xuat lai trong o trang thai cua danh sach CV (FR-C05 UI.md muc 4e, 6, 9). Chi render voi CV
// da xu ly xong. Vung aria-live luon co mat (ke ca khi rong) de trinh doc man hinh doc duoc thay doi:
// "Da cap nhat..." (thanh cong - voi nguoi nhin, nut va dong "phien ban cu" tu bien mat), cau loi that bai
// cua yeu cau, hoac loi khi gui yeu cau.
export function ResumeReparseStatus({ resume, justCompleted, requestError }: ResumeReparseStatusProps) {
  if (resume.parseStatus !== 'DONE') {
    return null
  }
  const legacy = canReparse(resume)
  const active = legacy && isReparseActive(resume)
  const failed = legacy && resume.reparse?.status === 'FAILED'

  return (
    <div className="flex flex-col gap-1">
      {active && <LinearProgress label={REPARSE_TEXT.progressLabel} />}
      {legacy && !active && !failed && (
        <p className="text-m3-body-sm text-m3-on-surface-variant">{REPARSE_TEXT.legacyNote}</p>
      )}
      <div aria-live="polite" className="flex flex-col gap-1">
        {failed && (
          <p className="text-m3-body-sm text-m3-on-surface">{reparseFailureText(resume.reparse?.errorMessage ?? null)}</p>
        )}
        {/* Nam trong hang bang (hover #F8F8F8): chu do chi dat 4.46:1, tin hieu loi giu bang icon. */}
        {requestError && (
          <div role="alert" className="flex items-start gap-1.5 text-m3-body-sm text-m3-on-surface">
            <AlertCircle className="mt-px h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            <p>{requestError}</p>
          </div>
        )}
        {justCompleted && !legacy && <span className="sr-only">{REPARSE_TEXT.success}</span>}
      </div>
    </div>
  )
}
