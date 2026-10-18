import { Download } from 'lucide-react'
import { useCallback } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { downloadPreviewPdf } from './api'
import { CV_BUILDER_TEXT } from './cvBuilderLabels'

interface PreviewDialogProps {
  // PDF cua B3; null = hop thoai dong.
  blob: Blob | null
  onClose: () => void
}

// FR-U12 UI.md muc 4b - hop thoai xem truoc (chi >= 600px; compact tai file thay vi mo - muc 8). PDF hien bang iframe
// tro toi URL blob: tao khi iframe gan vao DOM (hop thoai mo), thu hoi khi iframe roi DOM - dong hop thoai hoac trang
// unmount deu chay cleanup cua callback ref (React 19). Khong dung state/effect de giu URL: StrictMode goi lai ref
// callback (gan - go - gan) nen moi lan gan co URL rieng, khong URL nao bi thu hoi khi con dung.
export function PreviewDialog({ blob, onClose }: PreviewDialogProps) {
  const attachFrame = useCallback(
    (frame: HTMLIFrameElement | null) => {
      if (frame === null || blob === null) {
        return
      }
      const url = URL.createObjectURL(blob)
      frame.src = url
      return () => {
        URL.revokeObjectURL(url)
      }
    },
    [blob],
  )

  return (
    <Dialog open={blob !== null} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="grid h-[85vh] grid-rows-[auto_minmax(0,1fr)_auto] sm:max-w-[90vw] lg:max-w-4xl">
        <DialogHeader>
          <DialogTitle className="text-m3-on-surface">{CV_BUILDER_TEXT.previewTitle}</DialogTitle>
          <DialogDescription className="text-m3-on-surface">{CV_BUILDER_TEXT.previewNotice}</DialogDescription>
        </DialogHeader>
        <iframe
          ref={attachFrame}
          title={CV_BUILDER_TEXT.previewFrameTitle}
          className="h-full w-full rounded-m3-sm border border-m3-outline-variant bg-m3-surface"
        />
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            className="min-h-10"
            disabled={blob === null}
            onClick={() => blob && downloadPreviewPdf(blob)}
          >
            <Download className="h-4 w-4" aria-hidden="true" />
            {CV_BUILDER_TEXT.previewDownload}
          </Button>
          <Button type="button" className="min-h-10" onClick={onClose}>
            {CV_BUILDER_TEXT.previewClose}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
