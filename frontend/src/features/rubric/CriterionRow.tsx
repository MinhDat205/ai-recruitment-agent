import { isAxiosError } from 'axios'
import { GripVertical, Pencil, Trash2 } from 'lucide-react'
import { useState, type DragEvent } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { TableCell, TableRow } from '@/components/ui/table'
import { CriterionFormDialog } from './CriterionFormDialog'
import { useDeleteRubricCriterionMutation } from './ownerQueries'
import type { RubricCriterionResponse } from './ownerTypes'

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

function formatWeight(value: number): string {
  return Number(value.toFixed(2)).toString()
}

interface CriterionRowProps {
  jobId: string
  criterion: RubricCriterionResponse
  otherCriteriaWeight: number
  locked: boolean
  draggable: boolean
  onDragStart: () => void
  onDragOver: (e: DragEvent<HTMLTableRowElement>) => void
  onDrop: () => void
}

export function CriterionRow({
  jobId,
  criterion,
  otherCriteriaWeight,
  locked,
  draggable,
  onDragStart,
  onDragOver,
  onDrop,
}: CriterionRowProps) {
  const [editOpen, setEditOpen] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const deleteMutation = useDeleteRubricCriterionMutation(jobId)

  function handleDelete() {
    deleteMutation.mutate(criterion.id, {
      onSuccess: () => setConfirmDeleteOpen(false),
    })
  }

  const scaleSummary =
    criterion.scaleDescription && criterion.scaleDescription.length > 0
      ? `${criterion.scaleDescription.length} mức đã mô tả`
      : 'Thang điểm mặc định'

  return (
    <TableRow draggable={draggable} onDragStart={onDragStart} onDragOver={onDragOver} onDrop={onDrop}>
      <TableCell className="w-8">
        {draggable && (
          <GripVertical className="size-4 cursor-grab text-ink-muted" aria-hidden="true" />
        )}
      </TableCell>
      <TableCell>
        <p className="font-medium text-ink">{criterion.name}</p>
        {criterion.description && <p className="text-xs text-ink-muted italic">{criterion.description}</p>}
      </TableCell>
      <TableCell className="text-ink">{formatWeight(criterion.weight)}%</TableCell>
      <TableCell className="text-ink-muted">{criterion.maxScore}</TableCell>
      <TableCell className="text-ink-muted">{scaleSummary}</TableCell>
      {!locked && (
        <TableCell className="text-right">
          <div className="flex flex-col items-end gap-1">
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="icon-sm"
                aria-label="Sửa tiêu chí"
                onClick={() => setEditOpen(true)}
              >
                <Pencil />
              </Button>
              <Dialog open={confirmDeleteOpen} onOpenChange={setConfirmDeleteOpen}>
                <DialogTrigger asChild>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label="Xoá tiêu chí"
                    className="border-danger text-danger hover:bg-danger/10"
                  >
                    <Trash2 />
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Xoá tiêu chí?</DialogTitle>
                    <DialogDescription>
                      "{criterion.name}" sẽ bị xoá khỏi rubric. Hành động này không hoàn tác được.
                    </DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button type="button" variant="outline" onClick={() => setConfirmDeleteOpen(false)}>
                      Huỷ
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      disabled={deleteMutation.isPending}
                      onClick={handleDelete}
                    >
                      {deleteMutation.isPending ? 'Đang xoá...' : 'Xoá'}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>
            {deleteMutation.isError && (
              <p className="text-xs text-danger">{extractErrorMessage(deleteMutation.error, 'Xoá thất bại.')}</p>
            )}
          </div>
        </TableCell>
      )}

      <CriterionFormDialog
        jobId={jobId}
        open={editOpen}
        onOpenChange={setEditOpen}
        otherCriteriaWeight={otherCriteriaWeight}
        nextDisplayOrder={criterion.displayOrder}
        criterion={criterion}
      />
    </TableRow>
  )
}
