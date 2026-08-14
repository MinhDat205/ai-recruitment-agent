import { isAxiosError } from 'axios'
import { useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PublicLayout } from '../components/layout/PublicLayout'
import { ApplicationHistoryTimeline } from '../features/applications/ApplicationHistoryTimeline'
import { ApplicationStatusBadge } from '../features/applications/ApplicationStatusBadge'
import { useMyApplicationsQuery, useWithdrawApplicationMutation } from '../features/applications/queries'
import type { ApplicationSummary } from '../features/applications/types'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Backend tra loi qua ErrorResponse { error, message } (xem GlobalExceptionHandler), giong
// pattern extractErrorMessage trong JobApplyForm.tsx - khong tach util dung chung vi pham vi
// chi gioi han trong file nay.
function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

const WITHDRAWABLE_STATUSES: ApplicationSummary['status'][] = ['PENDING', 'INTERVIEW_INVITED']

export function CandidateApplicationsPage() {
  const { data: applications, isLoading, isError } = useMyApplicationsQuery()
  const [selected, setSelected] = useState<ApplicationSummary | null>(null)
  const [withdrawTarget, setWithdrawTarget] = useState<ApplicationSummary | null>(null)
  const withdrawMutation = useWithdrawApplicationMutation()

  const closeWithdrawDialog = () => {
    setWithdrawTarget(null)
    withdrawMutation.reset()
  }

  const confirmWithdraw = () => {
    if (!withdrawTarget) {
      return
    }
    withdrawMutation.mutate(withdrawTarget.id, {
      onSuccess: () => setWithdrawTarget(null),
    })
  }

  return (
    <PublicLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <Card>
          <CardHeader>
            <CardTitle>Đơn ứng tuyển của tôi</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading && <p className="text-sm text-ink-muted">Đang tải...</p>}
            {isError && <p className="text-sm text-danger">Không tải được danh sách đơn, vui lòng thử lại.</p>}
            {!isLoading && !isError && (!applications || applications.length === 0) && (
              <p className="text-sm text-ink-muted">Bạn chưa ứng tuyển vị trí nào.</p>
            )}
            {!isLoading && !isError && applications && applications.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Vị trí</TableHead>
                    <TableHead>Công ty</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead>Ngày nộp</TableHead>
                    <TableHead className="text-right">Hành động</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {applications.map((application) => (
                    <TableRow key={application.id}>
                      <TableCell>{application.jobTitle}</TableCell>
                      <TableCell className="text-ink-muted">{application.companyName}</TableCell>
                      <TableCell>
                        <ApplicationStatusBadge status={application.status} />
                      </TableCell>
                      <TableCell className="text-ink-muted">{formatAppliedAt(application.appliedAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelected(application)}>
                            Xem lịch sử
                          </Button>
                          {WITHDRAWABLE_STATUSES.includes(application.status) && (
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => setWithdrawTarget(application)}
                            >
                              Rút đơn
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      <Dialog open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Lịch sử ứng tuyển{selected ? ` — ${selected.jobTitle}` : ''}</DialogTitle>
          </DialogHeader>
          {selected && <ApplicationHistoryTimeline applicationId={selected.id} />}
        </DialogContent>
      </Dialog>

      <Dialog open={withdrawTarget !== null} onOpenChange={(open) => !open && closeWithdrawDialog()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Rút đơn ứng tuyển?</DialogTitle>
            <DialogDescription>
              Hành động này không thể hoàn tác. Sau khi rút, bạn sẽ không thể nộp lại đơn cho vị trí{' '}
              <span className="font-medium text-ink">{withdrawTarget?.jobTitle}</span> trong đợt tuyển hiện tại.
            </DialogDescription>
          </DialogHeader>
          {withdrawMutation.isError && (
            <p className="text-sm text-danger">
              {extractErrorMessage(withdrawMutation.error, 'Rút đơn thất bại, vui lòng thử lại.')}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={closeWithdrawDialog} disabled={withdrawMutation.isPending}>
              Huỷ
            </Button>
            <Button type="button" onClick={confirmWithdraw} disabled={withdrawMutation.isPending}>
              {withdrawMutation.isPending ? 'Đang rút đơn...' : 'Xác nhận rút đơn'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PublicLayout>
  )
}
