import { useState } from 'react'
import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { APPLICATION_STATUS_LABELS } from '../applications/applicationLabels'
import type { ApplicationStatus } from '../applications/types'
import { useHrJobsQuery } from '../jobs/ownerQueries'
import { useCriteriaNamesQuery } from './queries'
import type { CandidateSearchParams } from './types'

const ALL_JOBS = 'ALL'
const ALL_STATUS = 'ALL'
const NO_CRITERION = 'NONE'
const STATUS_OPTIONS = Object.keys(APPLICATION_STATUS_LABELS) as ApplicationStatus[]
// Trung khop JobOwnerService.MAX_SIZE (backend, da xac nhan luc lam Dot 3) - day la muc tran cua
// chinh backend, khong phai lua chon tuy y o day. Cong ty co hon 50 tin se bi cat bot trong
// dropdown nay (xem ghi chu "Dang hien thi..." ben duoi) - da ghi vao no ky thuat cho Dot 7,
// KHONG sua backend trong Dot 6 (ngoai pham vi FR-H08 frontend).
const JOB_DROPDOWN_SIZE = 50

interface FilterDraft {
  jobId: string
  status: string
  minTotalScore: string
  maxTotalScore: string
  criterionName: string
  minCriterionScore: string
}

const EMPTY_DRAFT: FilterDraft = {
  jobId: ALL_JOBS,
  status: ALL_STATUS,
  minTotalScore: '',
  maxTotalScore: '',
  criterionName: NO_CRITERION,
  minCriterionScore: '',
}

function toParams(draft: FilterDraft): CandidateSearchParams {
  return {
    jobId: draft.jobId === ALL_JOBS ? undefined : draft.jobId,
    status: draft.status === ALL_STATUS ? undefined : (draft.status as ApplicationStatus),
    minTotalScore: draft.minTotalScore === '' ? undefined : Number(draft.minTotalScore),
    maxTotalScore: draft.maxTotalScore === '' ? undefined : Number(draft.maxTotalScore),
    criterionName: draft.criterionName === NO_CRITERION ? undefined : draft.criterionName,
    minCriterionScore: draft.minCriterionScore === '' ? undefined : Number(draft.minCriterionScore),
  }
}

// criterionName va minCriterionScore phai CUNG bat/tat - backend (ApplicationSearchService.
// validateFilters) tra 400 neu chi co mot. Chan bang HAI lop doc lap, moi lop chan MOT huong lech
// (giu ca hai, khong bo lop nao - quyet dinh duyet Dot 6): (1) o nhap diem tieu chi disable cho
// toi khi chon tieu chi - chan huong "co diem ma chua chon tieu chi". (2) doi criterionName ve
// "Khong loc" tu xoa luon minCriterionScore trong CHINH onValueChange - chan huong "bo chon tieu
// chi khi da go diem". filterMismatch la lop CUOI, khong phai lop chinh - bat truong hop mot duong
// set draft moi sau nay lo quen ca hai lop tren, disable nut Ap dung + hien thong bao thay vi im
// lang gui sai.
export function CandidatesFilterBar({ onApply }: { onApply: (params: CandidateSearchParams) => void }) {
  const [draft, setDraft] = useState<FilterDraft>(EMPTY_DRAFT)
  const { data: jobsPage } = useHrJobsQuery({ size: JOB_DROPDOWN_SIZE })
  const { data: criteriaNames } = useCriteriaNamesQuery()

  const criterionSelected = draft.criterionName !== NO_CRITERION
  const minCriterionScoreFilled = draft.minCriterionScore !== ''
  const filterMismatch = criterionSelected !== minCriterionScoreFilled
  const jobListTruncated = Boolean(jobsPage) && jobsPage!.totalElements > jobsPage!.items.length

  function handleApply() {
    if (filterMismatch) return
    onApply(toParams(draft))
  }

  function handleReset() {
    setDraft(EMPTY_DRAFT)
    onApply({})
  }

  return (
    <div className="flex flex-col gap-3 rounded-(--radius-card) border border-line bg-surface p-4">
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
        <div className="flex flex-col gap-1">
          <Label>Tin tuyển dụng</Label>
          <Select value={draft.jobId} onValueChange={(value) => setDraft((d) => ({ ...d, jobId: value }))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_JOBS}>Tất cả tin</SelectItem>
              {jobsPage?.items.map((job) => (
                <SelectItem key={job.id} value={job.id}>
                  {job.title}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {jobListTruncated && (
            <p className="text-xs text-ink-muted">
              Đang hiển thị {jobsPage!.items.length} tin gần nhất trong tổng số {jobsPage!.totalElements} tin.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <Label>Trạng thái đơn</Label>
          <Select value={draft.status} onValueChange={(value) => setDraft((d) => ({ ...d, status: value }))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUS}>Tất cả trạng thái</SelectItem>
              {STATUS_OPTIONS.map((status) => (
                <SelectItem key={status} value={status}>
                  {APPLICATION_STATUS_LABELS[status]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="min-total-score">Tổng điểm từ</Label>
          <Input
            id="min-total-score"
            type="number"
            value={draft.minTotalScore}
            onChange={(e) => setDraft((d) => ({ ...d, minTotalScore: e.target.value }))}
            placeholder="Không giới hạn"
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="max-total-score">Tổng điểm đến</Label>
          <Input
            id="max-total-score"
            type="number"
            value={draft.maxTotalScore}
            onChange={(e) => setDraft((d) => ({ ...d, maxTotalScore: e.target.value }))}
            placeholder="Không giới hạn"
          />
        </div>

        <div className="flex flex-col gap-1">
          <Label>Tiêu chí</Label>
          <Select
            value={draft.criterionName}
            onValueChange={(value) =>
              setDraft((d) => ({
                ...d,
                criterionName: value,
                minCriterionScore: value === NO_CRITERION ? '' : d.minCriterionScore,
              }))
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_CRITERION}>Không lọc theo tiêu chí</SelectItem>
              {criteriaNames?.map((name) => (
                <SelectItem key={name} value={name}>
                  {name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="min-criterion-score">Điểm tiêu chí từ</Label>
          <Input
            id="min-criterion-score"
            type="number"
            value={draft.minCriterionScore}
            onChange={(e) => setDraft((d) => ({ ...d, minCriterionScore: e.target.value }))}
            disabled={!criterionSelected}
            placeholder={criterionSelected ? 'Bắt buộc' : 'Chọn tiêu chí trước'}
          />
        </div>
      </div>

      {filterMismatch && (
        <p className="text-xs text-danger">Phải chọn cả tiêu chí lẫn điểm tối thiểu, hoặc bỏ trống cả hai.</p>
      )}

      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={handleReset}>
          Xóa bộ lọc
        </Button>
        <Button type="button" onClick={handleApply} disabled={filterMismatch}>
          Áp dụng
        </Button>
      </div>
    </div>
  )
}
