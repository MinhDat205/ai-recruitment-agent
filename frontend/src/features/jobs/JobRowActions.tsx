import { isAxiosError } from 'axios'
import { useState } from 'react'
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
import { NEXT_STATUS_ACTIONS } from './jobLabels'
import { useChangeHrJobStatusMutation, useDeleteHrJobMutation } from './ownerQueries'
import type { JobOwnerResponse } from './ownerTypes'

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

export function JobRowActions({ job }: { job: JobOwnerResponse }) {
  const changeStatusMutation = useChangeHrJobStatusMutation()
  const deleteMutation = useDeleteHrJobMutation()
  const [confirmOpen, setConfirmOpen] = useState(false)

  const nextActions = NEXT_STATUS_ACTIONS[job.status]

  function handleDelete() {
    deleteMutation.mutate(job.id, {
      onSuccess: () => setConfirmOpen(false),
    })
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center gap-2">
        {nextActions.map((action) => (
          <Button
            key={action.status}
            type="button"
            variant="outline"
            size="sm"
            disabled={changeStatusMutation.isPending}
            onClick={() => changeStatusMutation.mutate({ id: job.id, status: action.status })}
          >
            {action.label}
          </Button>
        ))}

        <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
          <DialogTrigger asChild>
            <Button type="button" variant="outline" size="sm" className="border-danger text-danger hover:bg-danger/10">
              Xoá
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Xoá tin tuyển dụng?</DialogTitle>
              {/* Xoa mem o backend (deleted_at) - noi dung phai phan anh dung ban chat, KHONG
                  duoc noi "xoa vinh vien". */}
              <DialogDescription>
                "{job.title}" sẽ bị ẩn khỏi danh sách tin tuyển dụng và khỏi trang công khai. Dữ liệu vẫn được lưu
                lại trong hệ thống, không bị xoá vĩnh viễn.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConfirmOpen(false)}>
                Huỷ
              </Button>
              <Button type="button" variant="destructive" disabled={deleteMutation.isPending} onClick={handleDelete}>
                {deleteMutation.isPending ? 'Đang xoá...' : 'Xoá tin'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {changeStatusMutation.isError && (
        <p className="text-xs text-danger">
          {extractErrorMessage(changeStatusMutation.error, 'Đổi trạng thái thất bại.')}
        </p>
      )}
      {deleteMutation.isError && (
        <p className="text-xs text-danger">{extractErrorMessage(deleteMutation.error, 'Xoá thất bại.')}</p>
      )}
    </div>
  )
}
