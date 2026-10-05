import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { formatScore } from '../../lib/score'
import { JobStatusBadge } from '../jobs/JobStatusBadge'
import type { JobPerformanceItem } from './types'

export function JobPerformanceTable({ items }: { items: JobPerformanceItem[] }) {
  if (items.length === 0) {
    return <p className="text-sm text-m3-on-surface-variant">Chưa có tin tuyển dụng nào.</p>
  }

  return (
    <div>
      <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tin tuyển dụng</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead>Chu kỳ</TableHead>
              <TableHead className="text-right">Số đơn</TableHead>
              <TableHead className="text-right">Đã chấm xong</TableHead>
              <TableHead className="text-right">Điểm trung bình</TableHead>
              <TableHead className="text-right">Đã từng mời PV</TableHead>
              <TableHead className="text-right">Đã từng trúng tuyển</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.jobId}>
                <TableCell className="font-medium text-m3-on-surface">{item.title}</TableCell>
                <TableCell>
                  <JobStatusBadge status={item.status} />
                </TableCell>
                <TableCell className="text-m3-on-surface-variant">{item.recruitmentCycle}</TableCell>
                <TableCell className="text-right text-m3-on-surface-variant">{item.totalApplications}</TableCell>
                <TableCell className="text-right text-m3-on-surface-variant">{item.scoredApplications}</TableCell>
                <TableCell className="text-right text-m3-on-surface-variant">{formatScore(item.averageScore)}</TableCell>
                <TableCell className="text-right text-m3-on-surface-variant">{item.everInvitedCount}</TableCell>
                <TableCell className="text-right text-m3-on-surface-variant">{item.everHiredCount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {/* FR-U06: hai cot nay dem theo lich su chuyen trang thai (application_status_history),
          KHONG phai trang thai HIEN TAI cua don - chu thich de HR khong tu cong don voi
          StatusBreakdownChart (phan bo trang thai hien tai) roi thay venh so ma khong hieu tai
          sao. */}
      <p className="mt-2 text-xs text-m3-on-surface-variant">
        "Đã từng mời PV" và "Đã từng trúng tuyển" đếm theo lịch sử chuyển trạng thái (đơn đã từng
        đạt trạng thái đó), không phải trạng thái hiện tại — một đơn đã được mời phỏng vấn rồi rút
        đơn vẫn được tính vào "Đã từng mời PV".
      </p>
    </div>
  )
}
