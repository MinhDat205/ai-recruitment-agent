import { useState } from 'react'
import { Download, FileText, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatFileSize } from '../../lib/fileSize'

// The tep (UI.md muc 4a, 5d): nen m3-surface trong CA hai loai bong bong de chu phu (dung luong) dat tuong
// phan; vien m3-outline. Hai che do: trong tin da gui co nut tai (onDownload), trong khung soan co nut bo chon
// (onRemove). Ten dai cat bang truncate, ten day du o title (UI.md muc 8). Khong xem truoc anh/PDF (R-F10).
export function AttachmentChip({
  fileName,
  fileSize,
  onDownload,
  onRemove,
  disabled = false,
}: {
  fileName: string
  fileSize: number
  onDownload?: () => Promise<void>
  onRemove?: () => void
  disabled?: boolean
}) {
  const [isDownloading, setIsDownloading] = useState(false)

  async function handleDownload() {
    if (!onDownload) {
      return
    }
    setIsDownloading(true)
    try {
      await onDownload()
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <div className="flex max-w-full min-w-0 items-center gap-2 rounded-m3-sm border border-m3-outline bg-m3-surface px-2 py-1.5 text-m3-on-surface">
      <FileText className="h-4 w-4 shrink-0" aria-hidden="true" />
      <div className="flex min-w-0 flex-1 flex-col sm:flex-row sm:items-center sm:gap-1">
        <span className="truncate text-sm" title={fileName}>
          {fileName}
        </span>
        <span className="hidden text-sm text-m3-on-surface-variant sm:inline" aria-hidden="true">
          ·
        </span>
        <span className="shrink-0 text-xs text-m3-on-surface-variant sm:text-sm">{formatFileSize(fileSize)}</span>
      </div>
      {onDownload && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          onClick={handleDownload}
          disabled={isDownloading}
          aria-label={`Tải tệp ${fileName}`}
        >
          <Download aria-hidden="true" />
        </Button>
      )}
      {onRemove && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          onClick={onRemove}
          disabled={disabled}
          aria-label="Bỏ tệp đính kèm"
        >
          <X aria-hidden="true" />
        </Button>
      )}
    </div>
  )
}
