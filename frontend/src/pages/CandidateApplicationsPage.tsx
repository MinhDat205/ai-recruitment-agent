import { useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PublicLayout } from '../components/layout/PublicLayout'
import { ApplicationHistoryTimeline } from '../features/applications/ApplicationHistoryTimeline'
import { ApplicationStatusBadge } from '../features/applications/ApplicationStatusBadge'
import { useMyApplicationsQuery } from '../features/applications/queries'
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

export function CandidateApplicationsPage() {
  const { data: applications, isLoading, isError } = useMyApplicationsQuery()
  const [selected, setSelected] = useState<ApplicationSummary | null>(null)

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
                        <Button type="button" variant="outline" size="sm" onClick={() => setSelected(application)}>
                          Xem lịch sử
                        </Button>
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
    </PublicLayout>
  )
}
