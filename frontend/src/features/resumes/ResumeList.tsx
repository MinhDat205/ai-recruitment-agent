import { useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Download, FileSearch, FileText, RefreshCw, RotateCw, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadResumeRequest } from './api'
import { ParseStatusBadge } from './ParseStatusBadge'
import {
  isReparseActive,
  isResumeStalled,
  resumeParsedDataQueryKey,
  useReparseResumeMutation,
  useResumesQuery,
  useRetryResumeMutation,
  useSetPrimaryResumeMutation,
} from './queries'
import { ResumeParsedDataDialog } from './ResumeParsedDataDialog'
import { ResumeReparseStatus } from './ResumeReparseStatus'
import { canReparse, REPARSE_TEXT } from './resumeReparse'
import type { Resume } from './types'

function formatFileSize(bytes: number | null): string {
  if (bytes === null) {
    return ''
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatUploadedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

// Id cac CV co yeu cau trich xuat lai dang PENDING/RUNNING o lan tai truoc va nay da DONE.
function newlyCompletedReparseIds(previous: Resume[] | undefined, current: Resume[] | undefined): string[] {
  const wasActive = new Set((previous ?? []).filter(isReparseActive).map((resume) => resume.id))
  return (current ?? [])
    .filter((resume) => wasActive.has(resume.id) && resume.reparse?.status === 'DONE')
    .map((resume) => resume.id)
}

export function ResumeList() {
  const { data: resumes, isLoading, refetch, isFetching } = useResumesQuery()
  const setPrimaryMutation = useSetPrimaryResumeMutation()
  const retryMutation = useRetryResumeMutation()
  const reparseMutation = useReparseResumeMutation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [retryingId, setRetryingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [viewingResume, setViewingResume] = useState<{ id: string; fileName: string } | null>(null)
  const [reparsingId, setReparsingId] = useState<string | null>(null)
  const [reparseError, setReparseError] = useState<{ id: string; message: string } | null>(null)
  // FR-C05 - phat hien trich xuat lai vua xong bang cach so sanh voi danh sach lan truoc (mau "dieu chinh
  // state khi du lieu doi" cua React, khong dung effect de setState). Danh sach tu cap nhat qua
  // refetchInterval san co (queries.ts), khong co co che poll rieng.
  const [previousResumes, setPreviousResumes] = useState<Resume[] | undefined>(resumes)
  const [completedReparseIds, setCompletedReparseIds] = useState<string[]>([])
  if (resumes !== previousResumes) {
    const completed = newlyCompletedReparseIds(previousResumes, resumes)
    setPreviousResumes(resumes)
    if (completed.length > 0) {
      setCompletedReparseIds((ids) => [...ids, ...completed.filter((id) => !ids.includes(id))])
    }
  }

  // Du lieu trich xuat cua CV vua cap nhat da doi - bo cache dialog de lan mo sau tai ban moi.
  useEffect(() => {
    for (const id of completedReparseIds) {
      queryClient.invalidateQueries({ queryKey: resumeParsedDataQueryKey(id) })
    }
  }, [completedReparseIds, queryClient])

  async function handleReparse(resumeId: string) {
    setReparseError(null)
    setReparsingId(resumeId)
    try {
      await reparseMutation.mutateAsync(resumeId)
    } catch (err) {
      // 409/429: hien nguyen thong diep backend duoi dong CV (UI.md muc 6).
      setReparseError({ id: resumeId, message: extractErrorMessage(err, REPARSE_TEXT.requestFallbackError) })
    } finally {
      setReparsingId(null)
    }
  }

  async function handleRetry(resumeId: string) {
    setError(null)
    setRetryingId(resumeId)
    try {
      await retryMutation.mutateAsync(resumeId)
    } catch (err) {
      setError(extractErrorMessage(err, 'Không thể thử lại phân tích, vui lòng thử lại.'))
    } finally {
      setRetryingId(null)
    }
  }

  async function handleDownload(resume: Resume) {
    setError(null)
    setDownloadingId(resume.id)
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
    } catch (err) {
      setError(extractErrorMessage(err, 'Tải CV thất bại, vui lòng thử lại.'))
    } finally {
      setDownloadingId(null)
    }
  }

  if (isLoading) {
    return <p className="text-sm text-ink-muted">Đang tải danh sách CV...</p>
  }

  if (!resumes || resumes.length === 0) {
    return <p className="text-sm text-ink-muted">Chưa có CV nào. Tải lên CV đầu tiên của bạn ở trên.</p>
  }

  return (
    <div className="flex flex-col gap-2">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tên file</TableHead>
            <TableHead>Trạng thái xử lý</TableHead>
            <TableHead>Ngày tải lên</TableHead>
            <TableHead>Kích thước</TableHead>
            <TableHead className="text-right">Hành động</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {resumes.map((resume) => (
            <TableRow key={resume.id}>
              <TableCell>
                <div className="flex items-center gap-2">
                  <FileText className="h-4 w-4 shrink-0 text-ink-muted" aria-hidden="true" />
                  <span>{resume.fileName}</span>
                  {resume.isPrimary && (
                    <span className="rounded-(--radius-badge) bg-brand-light px-2 py-0.5 text-xs font-medium text-brand">
                      CV chính
                    </span>
                  )}
                </div>
              </TableCell>
              <TableCell>
                <div className="flex flex-col gap-1">
                  <ParseStatusBadge status={resume.parseStatus} />
                  {resume.parseStatus === 'FAILED' && resume.parseError && (
                    <p className="text-xs text-ink-muted">{resume.parseError}</p>
                  )}
                  {isResumeStalled(resume) && (
                    <div className="flex items-center gap-2">
                      <p className="text-xs text-ink-muted">Quá trình xử lý lâu hơn dự kiến</p>
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 text-xs font-medium text-brand hover:underline disabled:opacity-50"
                        disabled={isFetching}
                        onClick={() => refetch()}
                      >
                        <RotateCw className="h-3 w-3" aria-hidden="true" />
                        Kiểm tra lại
                      </button>
                    </div>
                  )}
                  <ResumeReparseStatus
                    resume={resume}
                    justCompleted={completedReparseIds.includes(resume.id)}
                    requestError={reparseError?.id === resume.id ? reparseError.message : null}
                  />
                </div>
              </TableCell>
              <TableCell className="text-ink-muted">{formatUploadedAt(resume.uploadedAt)}</TableCell>
              <TableCell className="text-ink-muted">{formatFileSize(resume.fileSize)}</TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-2">
                  {!resume.isPrimary && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={setPrimaryMutation.isPending}
                      onClick={() => setPrimaryMutation.mutate(resume.id)}
                    >
                      Đặt làm CV chính
                    </Button>
                  )}
                  {resume.parseStatus === 'FAILED' && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={retryingId === resume.id}
                      onClick={() => handleRetry(resume.id)}
                    >
                      <RotateCw className="h-4 w-4" aria-hidden="true" />
                      Phân tích lại
                    </Button>
                  )}
                  {canReparse(resume) && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={isReparseActive(resume) || reparsingId === resume.id}
                      onClick={() => handleReparse(resume.id)}
                    >
                      <RefreshCw className="h-4 w-4" aria-hidden="true" />
                      {isReparseActive(resume) || reparsingId === resume.id
                        ? REPARSE_TEXT.buttonRunning
                        : resume.reparse?.status === 'FAILED'
                          ? REPARSE_TEXT.buttonRetry
                          : REPARSE_TEXT.button}
                    </Button>
                  )}
                  {resume.parseStatus === 'DONE' && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setViewingResume({ id: resume.id, fileName: resume.fileName })}
                    >
                      <FileSearch className="h-4 w-4" aria-hidden="true" />
                      Xem dữ liệu đã trích xuất
                    </Button>
                  )}
                  {resume.parseStatus === 'DONE' && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => navigate(`/candidate/resumes/${resume.id}/improvement-suggestions`)}
                    >
                      <Sparkles className="h-4 w-4" aria-hidden="true" />
                      Gợi ý cải thiện CV
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Tải xuống ${resume.fileName}`}
                    disabled={downloadingId === resume.id}
                    onClick={() => handleDownload(resume)}
                  >
                    <Download className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {error && <p className="text-sm text-danger">{error}</p>}
      {viewingResume && (
        <ResumeParsedDataDialog
          resumeId={viewingResume.id}
          fileName={viewingResume.fileName}
          open={viewingResume !== null}
          onOpenChange={(open) => {
            if (!open) {
              setViewingResume(null)
            }
          }}
        />
      )}
    </div>
  )
}
