import { EvidenceQuoteList } from '../scoring/EvidenceQuoteList'
import { COMPARE_CRITERION_TEXT, MISSING_DATA } from './comparisonLabels'
import type { ComparisonCriterionCell } from './types'

// Mot o tieu chi (R-D4, R-C4, R-C5). So diem/trong so giu nguyen so backend tra (nhu CriterionScoreBreakdown), cung
// mot kieu chu o moi cot - khong className nao phu thuoc gia tri score (R-W1).
// - cell null (R-C4): "Chua co du lieu" + cau phien ban rubric, khong co vung mo rong.
// - Ten cua don khac ten o tieu de hang (R-C5): dong phu "Ten trong rubric cua don: ...".
// - Vung mo rong (ly giai + evidence) luon nam trong DOM voi id rieng de nut cua hang tro aria-controls toi; an bang
//   thuoc tinh hidden khi dong. Trang thai mo do trang giu theo khoa hang (UI.md muc 8).
// onDiffRow: o nam trong hang "Khac nhau" (nen surface-container) -> moi chu dung text-m3-on-surface (UI.md 5d).
export function CriterionCompareCell({
  cell,
  rowName,
  expanded,
  regionId,
  onDiffRow,
  inline = false,
}: {
  cell: ComparisonCriterionCell | null
  rowName: string
  expanded: boolean
  regionId: string
  onDiffRow: boolean
  // Bo cuc theo hang (4c): diem va trong so cung mot dong "4/5 diem · Trong so 35%".
  inline?: boolean
}) {
  const secondaryText = onDiffRow ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'

  if (cell === null) {
    return (
      <div className="flex flex-col gap-1">
        <p className={`text-m3-body-md ${secondaryText}`}>{MISSING_DATA}</p>
        <p className={`text-m3-body-sm ${secondaryText}`}>{COMPARE_CRITERION_TEXT.missingCriterion}</p>
      </div>
    )
  }

  const scoreText = COMPARE_CRITERION_TEXT.score(String(cell.score), cell.maxScore)
  const weightText = COMPARE_CRITERION_TEXT.weight(String(cell.weight))

  return (
    <div className="flex min-w-0 flex-col gap-1">
      {cell.criterionName !== rowName && (
        <p className={`text-m3-body-sm ${secondaryText}`}>{COMPARE_CRITERION_TEXT.nameInRubric(cell.criterionName)}</p>
      )}
      {inline ? (
        <p className="text-m3-body-md text-m3-on-surface">{`${scoreText} · ${weightText}`}</p>
      ) : (
        <>
          <p className="text-m3-body-md text-m3-on-surface">{scoreText}</p>
          <p className="text-m3-body-md text-m3-on-surface">{weightText}</p>
        </>
      )}
      <div id={regionId} hidden={!expanded} className="mt-2 break-words">
        <p className={`text-xs ${secondaryText}`}>{COMPARE_CRITERION_TEXT.aiReasoningNote}</p>
        {/* reasoning hien NGUYEN VAN nhu FR-H06 - plain text, khong loc/sua/tom tat. */}
        <p className="mt-1 text-sm whitespace-pre-wrap text-m3-on-surface">{cell.reasoning}</p>
        <EvidenceQuoteList evidence={cell.evidence} emptyTextClassName={secondaryText} />
      </div>
    </div>
  )
}
