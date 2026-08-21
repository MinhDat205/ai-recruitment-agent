import { useState } from 'react'
import { FileText } from 'lucide-react'
import { Button } from '../../components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { ScoringRunAuditPanel } from './ScoringRunAuditPanel'
import type { CandidateSearchItem } from './types'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// null = CHUA CO luot DONE nao (khac 0 diem that) - hien "Chưa chấm", khong hien "0" (cung quy
// uoc voi JobPerformanceTable). KHONG co cot Hang: FR-H05 chi dinh nghia xep hang trong PHAM VI
// MOT chien dich (mot job) - danh sach nay xuyen nhieu job nen khong hien rank (quyet dinh #2
// trong plan Dot 3, ap dung lai o day).
function formatTotalScore(totalScore: number | null): string {
  return totalScore === null ? 'Chưa chấm' : totalScore.toFixed(3)
}

export function CandidatesTable({ items }: { items: CandidateSearchItem[] }) {
  const [auditTarget, setAuditTarget] = useState<CandidateSearchItem | null>(null)

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-1 rounded-(--radius-card) border border-line bg-surface py-12 text-center">
        <p className="text-sm text-ink-muted">Không có ứng viên nào khớp với bộ lọc hiện tại.</p>
      </div>
    )
  }

  return (
    <>
      <div className="rounded-(--radius-card) border border-line bg-surface">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Ứng viên</TableHead>
              <TableHead>Tin tuyển dụng</TableHead>
              <TableHead>Ngày nộp</TableHead>
              <TableHead>Trạng thái CV</TableHead>
              <TableHead>Trạng thái đơn</TableHead>
              <TableHead>Tổng điểm</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id}>
                <TableCell className="text-ink">{item.candidateName}</TableCell>
                <TableCell className="text-ink-muted">{item.jobTitle}</TableCell>
                <TableCell className="text-ink-muted">{formatAppliedAt(item.appliedAt)}</TableCell>
                <TableCell>
                  <ParseStatusBadge status={item.resumeParseStatus} />
                </TableCell>
                <TableCell>
                  <ApplicationStatusBadge status={item.status} />
                </TableCell>
                <TableCell className="text-ink">{formatTotalScore(item.totalScore)}</TableCell>
                <TableCell className="text-right">
                  <Button type="button" variant="outline" size="sm" onClick={() => setAuditTarget(item)}>
                    <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                    Lịch sử đánh giá
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <ScoringRunAuditPanel application={auditTarget} onOpenChange={(open) => !open && setAuditTarget(null)} />
    </>
  )
}
