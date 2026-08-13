import { isAxiosError } from 'axios'
import { Download, FileText } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadResumeRequest } from './api'
import { useResumesQuery, useSetPrimaryResumeMutation } from './queries'
import { PARSE_STATUS_LABELS, PARSE_STATUS_STYLES } from './resumeLabels'
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

export function ResumeList() {
  const { data: resumes, isLoading } = useResumesQuery()
  const setPrimaryMutation = useSetPrimaryResumeMutation()
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

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
                <span
                  className={`inline-flex items-center rounded-(--radius-badge) px-2 py-1 text-xs font-medium ${PARSE_STATUS_STYLES[resume.parseStatus]}`}
                >
                  {PARSE_STATUS_LABELS[resume.parseStatus]}
                </span>
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
    </div>
  )
}
