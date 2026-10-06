import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useResumeParsedDataQuery } from './queries'
import { ResumeParsedDataSkeleton, ResumeParsedDataView } from './ResumeParsedDataView'

// Than hop thoai tach sang ResumeParsedDataView (FR-H09 R-C5); hop thoai van tu goi
// useResumeParsedDataQuery nhu truoc.
interface ResumeParsedDataDialogProps {
  resumeId: string
  fileName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ResumeParsedDataDialog({ resumeId, fileName, open, onOpenChange }: ResumeParsedDataDialogProps) {
  const { data, isLoading } = useResumeParsedDataQuery(resumeId, open)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Dữ liệu đã trích xuất — {fileName}</DialogTitle>
        </DialogHeader>

        {isLoading && <ResumeParsedDataSkeleton />}

        {!isLoading && !data && <p className="text-sm text-m3-on-surface-variant">Chưa có dữ liệu trích xuất cho CV này.</p>}

        {!isLoading && data && <ResumeParsedDataView data={data} />}
      </DialogContent>
    </Dialog>
  )
}
