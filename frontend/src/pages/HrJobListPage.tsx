import { Link } from 'react-router-dom'
import { useState } from 'react'
import { HrLayout } from '../components/layout/HrLayout'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { JobRowActions } from '../features/jobs/JobRowActions'
import { JobStatusBadge } from '../features/jobs/JobStatusBadge'
import { EMPLOYMENT_TYPE_LABELS, JOB_STATUS_LABELS, JOB_STATUS_OPTIONS } from '../features/jobs/jobLabels'
import { useHrJobsQuery } from '../features/jobs/ownerQueries'
import type { JobStatus } from '../features/jobs/ownerTypes'
import { Pagination } from '../features/jobs/Pagination'
import { formatDeadline } from '@/lib/date'

const PAGE_SIZE = 10
const ALL_STATUS = 'ALL'

function formatSalary(min: number | null, max: number | null, currency: string | null): string {
  if (min == null && max == null) {
    return '—'
  }
  const cur = currency ?? 'VND'
  const fmt = (value: number) => value.toLocaleString('vi-VN')
  if (min != null && max != null) {
    return `${fmt(min)} - ${fmt(max)} ${cur}`
  }
  return `${fmt((min ?? max) as number)} ${cur}`
}

export function HrJobListPage() {
  const [statusFilter, setStatusFilter] = useState<JobStatus | typeof ALL_STATUS>(ALL_STATUS)
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useHrJobsQuery({
    status: statusFilter === ALL_STATUS ? undefined : statusFilter,
    page,
    size: PAGE_SIZE,
  })

  function handleStatusChange(value: string) {
    setStatusFilter(value as JobStatus | typeof ALL_STATUS)
    setPage(0)
  }

  return (
    <HrLayout title="Tin tuyển dụng">
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <Select value={statusFilter} onValueChange={handleStatusChange}>
            <SelectTrigger className="w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUS}>Tất cả trạng thái</SelectItem>
              {JOB_STATUS_OPTIONS.map((status) => (
                <SelectItem key={status} value={status}>
                  {JOB_STATUS_LABELS[status]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button asChild>
            <Link to="/hr/jobs/new">Tạo tin mới</Link>
          </Button>
        </div>

        {isLoading && <p className="text-sm text-ink-muted">Đang tải...</p>}

        {isError && <p className="text-sm text-danger">Không tải được danh sách tin tuyển dụng, vui lòng thử lại.</p>}

        {!isLoading && !isError && data && data.items.length === 0 && (
          <div className="flex flex-col items-center gap-2 rounded-(--radius-card) border border-line bg-surface py-12 text-center">
            <p className="text-sm text-ink-muted">
              {statusFilter === ALL_STATUS ? 'Chưa có tin tuyển dụng nào.' : 'Không có tin nào ở trạng thái này.'}
            </p>
            <Link to="/hr/jobs/new" className="text-sm font-medium text-brand hover:underline">
              Tạo tin đầu tiên →
            </Link>
          </div>
        )}

        {!isLoading && !isError && data && data.items.length > 0 && (
          <div className="rounded-(--radius-card) border border-line bg-surface">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tiêu đề</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Địa điểm</TableHead>
                  <TableHead>Loại hình</TableHead>
                  <TableHead>Mức lương</TableHead>
                  <TableHead>Hạn nộp</TableHead>
                  <TableHead>Chu kỳ</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell>
                      <Link to={`/hr/jobs/${job.id}/edit`} className="font-medium text-ink hover:text-brand">
                        {job.title}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <JobStatusBadge status={job.status} />
                    </TableCell>
                    <TableCell className="text-ink-muted">{job.location ?? '—'}</TableCell>
                    <TableCell className="text-ink-muted">
                      {job.employmentType ? (EMPLOYMENT_TYPE_LABELS[job.employmentType] ?? job.employmentType) : '—'}
                    </TableCell>
                    <TableCell className="text-ink-muted">
                      {formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency)}
                    </TableCell>
                    <TableCell className="text-ink-muted">{formatDeadline(job.deadline)}</TableCell>
                    <TableCell className="text-ink-muted">{job.recruitmentCycle}</TableCell>
                    <TableCell>
                      <JobRowActions job={job} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        {data && <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />}
      </div>
    </HrLayout>
  )
}
