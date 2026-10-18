import { AlertCircle, ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  cvBuilderItemCounter,
  cvBuilderItemLegend,
  cvBuilderMoveDownLabel,
  cvBuilderMoveUpLabel,
  cvBuilderRemoveItemLabel,
  type CvBuilderBlock,
} from './cvBuilderLabels'

interface ListSectionProps {
  block: CvBuilderBlock
  title: string
  // id on dinh cua tung muc (useFieldArray fields[].id) - key cua muc khi doi cho.
  itemIds: string[]
  max: number
  addLabel: string
  emptyText: string
  // Loi cua ca khoi ("{Khối} tối đa {n} mục.") - chi xay ra khi dien san vuot gioi han.
  error?: string
  onAdd: () => void
  onMove: (from: number, to: number) => void
  onRemove: (index: number) => void
  renderItem: (index: number) => ReactNode
}

// FR-U12 UI.md muc 4a, 5a, 9 - khung khoi danh sach: section co aria-labelledby, moi muc la fieldset co legend
// "{Khối} {i}" trong khung vien outline-variant, nut len/xuong/xoa la icon button co aria-label. Xoa khong hoi xac nhan
// (form chua luu); sau khi xoa focus ve nut "Thêm …" cua khoi. Khoi rong hien cau rong + nut them.
export function ListSection({
  block,
  title,
  itemIds,
  max,
  addLabel,
  emptyText,
  error,
  onAdd,
  onMove,
  onRemove,
  renderItem,
}: ListSectionProps) {
  const headingId = useId()
  const errorId = useId()
  const addButtonRef = useRef<HTMLButtonElement>(null)
  // Nut "Thêm …" co the dang khoa (du max muc) luc bam xoa - focus sau lan render ke tiep, khi nut da mo lai.
  const focusAddAfterRemove = useRef(false)
  const count = itemIds.length

  useEffect(() => {
    if (focusAddAfterRemove.current) {
      focusAddAfterRemove.current = false
      addButtonRef.current?.focus()
    }
  }, [count])

  function handleRemove(index: number) {
    focusAddAfterRemove.current = true
    onRemove(index)
  }

  return (
    <Card>
      <section aria-labelledby={headingId} className="flex flex-col gap-4">
        <CardHeader>
          <CardTitle id={headingId} className="text-m3-title-md text-m3-on-surface">
            {title}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {count === 0 && <p className="text-sm text-m3-on-surface-variant">{emptyText}</p>}
          {itemIds.map((itemId, index) => {
            const n = index + 1
            const legend = cvBuilderItemLegend(block, n)
            return (
              <fieldset key={itemId} className="flex flex-col gap-4 rounded-m3-sm border border-m3-outline-variant p-3">
                {/* legend cho trinh doc man hinh; dong tieu de nhin thay o duoi nam cung hang voi nut. */}
                <legend className="sr-only">{legend}</legend>
                <div className="flex items-center justify-between gap-2">
                  <span aria-hidden="true" className="text-sm font-medium text-m3-on-surface">
                    {legend}
                  </span>
                  <div className="flex items-center gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-10"
                      aria-label={cvBuilderMoveUpLabel(block, n)}
                      disabled={index === 0}
                      onClick={() => onMove(index, index - 1)}
                    >
                      <ArrowUp aria-hidden="true" />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-10"
                      aria-label={cvBuilderMoveDownLabel(block, n)}
                      disabled={index === count - 1}
                      onClick={() => onMove(index, index + 1)}
                    >
                      <ArrowDown aria-hidden="true" />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-10"
                      aria-label={cvBuilderRemoveItemLabel(block, n)}
                      onClick={() => handleRemove(index)}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  </div>
                </div>
                {renderItem(index)}
              </fieldset>
            )
          })}
          {error && (
            <p id={errorId} role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-error">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
              {error}
            </p>
          )}
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <Button
              ref={addButtonRef}
              type="button"
              variant="outline"
              className="min-h-10 w-full sm:w-auto"
              disabled={count >= max}
              aria-describedby={error ? errorId : undefined}
              onClick={onAdd}
            >
              <Plus aria-hidden="true" />
              {addLabel}
            </Button>
            <span className="text-sm text-m3-on-surface-variant sm:text-right">{cvBuilderItemCounter(count, max)}</span>
          </div>
        </CardContent>
      </section>
    </Card>
  )
}
