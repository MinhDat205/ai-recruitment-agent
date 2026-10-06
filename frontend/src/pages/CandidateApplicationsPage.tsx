import { isAxiosError } from 'axios'
import { CalendarClock } from 'lucide-react'
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
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { ApplicationHistoryTimeline } from '../features/applications/ApplicationHistoryTimeline'
import { ApplicationStatusBadge } from '../features/applications/ApplicationStatusBadge'
import {
  useApplicationHistoryQuery,
  useMyApplicationsQuery,
  useWithdrawApplicationMutation,
} from '../features/applications/queries'
import type { ApplicationSummary } from '../features/applications/types'
import { useInterviewInvitationQuery } from '../features/interviewinvitation/queries'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Gio Viet Nam, hien day-thang-nam + gio-phut - dung format voi formatAppliedAt/formatChangedAt
// (ApplicationHistoryTimeline.tsx) de nhat quan toan trang.
function formatScheduledAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  })
}

// Dialog xem giay moi phong van - noi dung do HR soan, hien NGUYEN VAN (whitespace-pre-wrap giu
// xuong dong), khong render/tom tat lai gi them.
function InterviewInvitationDetailDialog({ applicationId }: { applicationId: string }) {
  const {
    data: invitation,
    isLoading,
    isError,
    error: invitationError,
  } = useInterviewInvitationQuery(applicationId, true)

  if (isLoading) {
    return <p className="text-sm text-m3-on-surface-variant">Đang tải giấy mời...</p>
  }

  if (isError) {
    // 404 INTERVIEW_INVITATION_NOT_FOUND: don co the bi REJECTED thang tu PENDING (khong qua
    // phong van) - khong phai loi, chi la khong co giay moi nao de xem. Loi khac (mang, 500...)
    // moi hien canh bao do.
    if (isAxiosError(invitationError) && invitationError.response?.status === 404) {
      return <p className="text-sm text-m3-on-surface-variant">Đơn này chưa có giấy mời phỏng vấn.</p>
    }
    return <p className="text-sm text-m3-error">Không tải được giấy mời, vui lòng thử lại.</p>
  }

  if (!invitation) {
    return null
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2 text-sm">
        <CalendarClock className="h-4 w-4 shrink-0 text-m3-primary" aria-hidden="true" />
        <span className="font-medium text-m3-on-surface">{formatScheduledAt(invitation.scheduledAt)}</span>
      </div>
      {invitation.location && (
        <p className="text-sm text-m3-on-surface-variant">
          <span className="font-medium text-m3-on-surface">Địa điểm: </span>
          {invitation.location}
        </p>
      )}
      <p className="text-sm font-medium text-m3-on-surface">{invitation.subject}</p>
      <p className="whitespace-pre-wrap text-sm text-m3-on-surface">{invitation.renderedContent}</p>
    </div>
  )
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

// Xem lai giay moi duoc o ca HIRED/REJECTED - ung vien phong van xong, HR chot ket qua, ho van
// can doi chieu lai lich hen cu. Backend khong loc theo status (xem 2 test
// get_applicationHiredAfterInterview_stillReturnsInvitation / ...Rejected...).
const INVITATION_VIEWABLE_STATUSES: ApplicationSummary['status'][] = ['INTERVIEW_INVITED', 'HIRED', 'REJECTED']

// FR-H09 R-C3: ApplicationHistoryTimeline chi con hien thi - trang ung vien tu goi
// useApplicationHistoryQuery o day. Van mount theo selected nhu truoc nen chi goi API khi mo hop thoai.
function CandidateApplicationHistory({ applicationId }: { applicationId: string }) {
  const { data: history, isLoading, isError } = useApplicationHistoryQuery(applicationId)
  return <ApplicationHistoryTimeline history={history} isLoading={isLoading} isError={isError} />
}

export function CandidateApplicationsPage() {
  const { data: applications, isLoading, isError } = useMyApplicationsQuery()
  const [selected, setSelected] = useState<ApplicationSummary | null>(null)
  const [withdrawTarget, setWithdrawTarget] = useState<ApplicationSummary | null>(null)
  const [invitationTarget, setInvitationTarget] = useState<ApplicationSummary | null>(null)
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
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <Card>
          <CardHeader>
            <CardTitle>Đơn ứng tuyển của tôi</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading && <p className="text-sm text-m3-on-surface-variant">Đang tải...</p>}
            {isError && <p className="text-sm text-m3-error">Không tải được danh sách đơn, vui lòng thử lại.</p>}
            {!isLoading && !isError && (!applications || applications.length === 0) && (
              <p className="text-sm text-m3-on-surface-variant">Bạn chưa ứng tuyển vị trí nào.</p>
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
                      <TableCell className="text-m3-on-surface-variant">{application.companyName}</TableCell>
                      <TableCell>
                        <ApplicationStatusBadge status={application.status} />
                      </TableCell>
                      <TableCell className="text-m3-on-surface-variant">{formatAppliedAt(application.appliedAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelected(application)}>
                            Xem lịch sử
                          </Button>
                          {INVITATION_VIEWABLE_STATUSES.includes(application.status) && (
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => setInvitationTarget(application)}
                            >
                              Xem giấy mời
                            </Button>
                          )}
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
          {selected && <CandidateApplicationHistory applicationId={selected.id} />}
        </DialogContent>
      </Dialog>

      <Dialog open={withdrawTarget !== null} onOpenChange={(open) => !open && closeWithdrawDialog()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Rút đơn ứng tuyển?</DialogTitle>
            <DialogDescription>
              Hành động này không thể hoàn tác. Sau khi rút, bạn sẽ không thể nộp lại đơn cho vị trí{' '}
              <span className="font-medium text-m3-on-surface">{withdrawTarget?.jobTitle}</span> trong đợt tuyển hiện tại.
            </DialogDescription>
          </DialogHeader>
          {withdrawMutation.isError && (
            <p className="text-sm text-m3-error">
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

      <Dialog open={invitationTarget !== null} onOpenChange={(open) => !open && setInvitationTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Giấy mời phỏng vấn{invitationTarget ? ` — ${invitationTarget.jobTitle}` : ''}</DialogTitle>
          </DialogHeader>
          {invitationTarget && <InterviewInvitationDetailDialog applicationId={invitationTarget.id} />}
        </DialogContent>
      </Dialog>
    </CandidateLayout>
  )
}
