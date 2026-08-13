import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { CardContent } from '@/components/ui/card'
import { Table, TableBody, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CriterionFormDialog } from './CriterionFormDialog'
import { CriterionRow } from './CriterionRow'
import { useReorderRubricCriteriaMutation, useRubricQuery } from './ownerQueries'
import type { RubricCriterionResponse } from './ownerTypes'

function formatWeight(value: number): string {
  return Number(value.toFixed(2)).toString()
}

function RubricWeightSummary({ totalWeight }: { totalWeight: number }) {
  const remaining = 100 - totalWeight
  return (
    <div className="flex flex-col gap-1 rounded-(--radius-card) border border-line bg-canvas px-4 py-3">
      <p className="text-sm font-medium text-ink">Tổng trọng số hiện tại: {formatWeight(totalWeight)}%</p>
      {/* Trung tinh, mang tinh thong tin - rubric dang xay do la trang thai binh thuong, khong
          phai loi. Chi dung mau canh bao khi that su vuot nguong (truong hop hiem, phong thu). */}
      {remaining > 1e-9 && (
        <p className="text-sm text-ink-muted">Còn thiếu {formatWeight(remaining)}% mới mở tin tuyển dụng được.</p>
      )}
      {Math.abs(remaining) <= 1e-9 && (
        <p className="text-sm text-ink-muted">Đã đủ 100% trọng số — sẵn sàng để mở tin tuyển dụng.</p>
      )}
      {remaining < -1e-9 && (
        <p className="text-sm text-danger">Tổng đang vượt 100%, vui lòng điều chỉnh lại trọng số.</p>
      )}
    </div>
  )
}

export function RubricTab({ jobId }: { jobId: string }) {
  const { data: rubric, isLoading, isError } = useRubricQuery(jobId)
  const reorderMutation = useReorderRubricCriteriaMutation(jobId)
  const [addOpen, setAddOpen] = useState(false)
  // Chi giu thu tu "lac quan" tam thoi trong luc mot luot keo-tha dang goi API - reset ve null
  // (dung lai thu tu tu server) ngay khi mutation ket thuc, thay vi dong bo lien tuc bang effect.
  const [pendingOrder, setPendingOrder] = useState<string[] | null>(null)
  const [draggedId, setDraggedId] = useState<string | null>(null)

  const criteriaById = useMemo(() => {
    const map = new Map<string, RubricCriterionResponse>()
    rubric?.criteria.forEach((c) => map.set(c.id, c))
    return map
  }, [rubric])

  const serverOrderedIds = useMemo(
    () => (rubric ? [...rubric.criteria].sort((a, b) => a.displayOrder - b.displayOrder).map((c) => c.id) : []),
    [rubric],
  )

  const orderedIds = pendingOrder ?? serverOrderedIds
  const orderedCriteria = orderedIds
    .map((id) => criteriaById.get(id))
    .filter((c): c is RubricCriterionResponse => Boolean(c))

  if (isLoading) {
    return <p className="p-6 text-sm text-ink-muted">Đang tải...</p>
  }
  if (isError || !rubric) {
    return <p className="p-6 text-sm text-danger">Không tải được rubric, vui lòng thử lại.</p>
  }

  function handleDrop(targetId: string) {
    if (!draggedId || draggedId === targetId) {
      setDraggedId(null)
      return
    }
    const fromIndex = orderedIds.indexOf(draggedId)
    const toIndex = orderedIds.indexOf(targetId)
    const next = [...orderedIds]
    next.splice(fromIndex, 1)
    next.splice(toIndex, 0, draggedId)
    setPendingOrder(next)
    setDraggedId(null)

    const nextCriteria = next.map((id) => criteriaById.get(id)).filter((c): c is RubricCriterionResponse => Boolean(c))
    reorderMutation.mutate(nextCriteria, { onSettled: () => setPendingOrder(null) })
  }

  return (
    <CardContent className="flex flex-col gap-4 pt-4">
      <RubricWeightSummary totalWeight={rubric.totalWeight} />

      {rubric.locked && (
        <div className="rounded-(--radius-card) border border-line bg-canvas px-4 py-3 text-sm text-ink-muted">
          Rubric đã khoá vì đã có lượt chấm điểm đầu tiên. Sửa tiêu chí lúc này sẽ làm sai lệch lịch sử đánh giá,
          nên form chỉ hiển thị để xem, không sửa được.
        </div>
      )}

      {!rubric.locked && (
        <div className="flex justify-end">
          <Button type="button" onClick={() => setAddOpen(true)}>
            <Plus /> Thêm tiêu chí
          </Button>
        </div>
      )}

      {orderedCriteria.length === 0 ? (
        <div className="flex flex-col items-center gap-1 rounded-(--radius-card) border border-line bg-surface py-12 text-center">
          <p className="text-sm text-ink-muted">Chưa có tiêu chí nào trong rubric.</p>
          {!rubric.locked && <p className="text-sm text-ink-muted">Bấm "Thêm tiêu chí" để bắt đầu.</p>}
        </div>
      ) : (
        <div className="rounded-(--radius-card) border border-line bg-surface">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-8" />
                <TableHead>Tên tiêu chí</TableHead>
                <TableHead>Trọng số</TableHead>
                <TableHead>Thang điểm</TableHead>
                <TableHead>Mô tả thang</TableHead>
                {!rubric.locked && <TableHead className="text-right">Thao tác</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {orderedCriteria.map((criterion) => (
                <CriterionRow
                  key={criterion.id}
                  jobId={jobId}
                  criterion={criterion}
                  otherCriteriaWeight={rubric.totalWeight - criterion.weight}
                  locked={rubric.locked}
                  draggable={!rubric.locked}
                  onDragStart={() => setDraggedId(criterion.id)}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={() => handleDrop(criterion.id)}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {reorderMutation.isError && <p className="text-sm text-danger">Sắp xếp lại thất bại, vui lòng thử lại.</p>}

      {!rubric.locked && (
        <CriterionFormDialog
          jobId={jobId}
          open={addOpen}
          onOpenChange={setAddOpen}
          otherCriteriaWeight={rubric.totalWeight}
          // KHONG dung criteria.length: sau khi da xoa 1 tieu chi o giua danh sach, length se
          // trung voi display_order con lai (vd con [0,2], length=2 lai trung tieu chi order=2).
          nextDisplayOrder={rubric.criteria.length === 0 ? 0 : Math.max(...rubric.criteria.map((c) => c.displayOrder)) + 1}
        />
      )}
    </CardContent>
  )
}
