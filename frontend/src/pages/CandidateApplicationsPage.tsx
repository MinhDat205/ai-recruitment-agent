import { useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { ApplicationHistoryTimeline } from '../features/applications/ApplicationHistoryTimeline'
import { ApplicationStatusBadge } from '../features/applications/ApplicationStatusBadge'
import { useApplicationHistoryQuery, useMyApplicationsQuery } from '../features/applications/queries'
import type { ApplicationSummary } from '../features/applications/types'
import { WithdrawApplicationDialog } from '../features/applications/WithdrawApplicationDialog'
import { InterviewInvitationDetails } from '../features/interviewinvitation/InterviewInvitationDetails'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
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

      <WithdrawApplicationDialog application={withdrawTarget} onClose={() => setWithdrawTarget(null)} />

      <Dialog open={invitationTarget !== null} onOpenChange={(open) => !open && setInvitationTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Giấy mời phỏng vấn{invitationTarget ? ` — ${invitationTarget.jobTitle}` : ''}</DialogTitle>
          </DialogHeader>
          {/* FR-U08 R-C2: component chung KHONG render gi khi chua co giay moi (404); hop thoai nay giu cau
              cu bang notFoundFallback cho toi khi bi bo o dot 5 (R-P3). */}
          {invitationTarget && (
            <InterviewInvitationDetails
              applicationId={invitationTarget.id}
              notFoundFallback={
                <p className="text-sm text-m3-on-surface-variant">Đơn này chưa có giấy mời phỏng vấn.</p>
              }
            />
          )}
        </DialogContent>
      </Dialog>
    </CandidateLayout>
  )
}
