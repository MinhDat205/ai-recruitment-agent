import { FileText } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { ApplicationStatusBadge } from '../features/applications/ApplicationStatusBadge'
import { useMyApplicationsQuery } from '../features/applications/queries'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// FR-U08 R-P3, R-E1 - lich su, giay moi va nut Rut don chi con o trang chi tiet don
// (/candidate/applications/:id); moi dong o day dung MOT lien ket "Xem chi tiết". Khong ton tai hai noi xem
// lich su/giay moi hay hai noi rut don.
export function CandidateApplicationsPage() {
  const { data: applications, isLoading, isError } = useMyApplicationsQuery()

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
                        <div className="flex justify-end">
                          <Button asChild variant="outline" size="sm">
                            <Link to={`/candidate/applications/${application.id}`}>
                              <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                              Xem chi tiết
                            </Link>
                          </Button>
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
    </CandidateLayout>
  )
}
