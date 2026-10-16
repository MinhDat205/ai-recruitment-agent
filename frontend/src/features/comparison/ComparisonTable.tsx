import { ChevronDown, ChevronRight, Info, Sparkles } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { formatTotalScore } from '../../lib/score'
import type { ExplanationPoint } from '../scoring/types'
import { ComparisonColumnHeader } from './ComparisonColumnHeader'
import {
  COMPARE_CRITERION_TEXT,
  COMPARE_EXPLANATION_TEXT,
  COMPARE_GROUP_TEXT,
  COMPARE_PAGE_TEXT,
  COMPARE_ROW_TEXT,
  COMPARE_SCREENING_TEXT,
  explanationStatusText,
  formatEducation,
  formatExperience,
  MISSING_DATA,
  screeningCellText,
  screeningQuestionLabel,
  screeningStatusText,
} from './comparisonLabels'
import { CriterionCompareCell } from './CriterionCompareCell'
import { DiffMark } from './DiffMark'
import type {
  ApplicationComparisonResponse,
  ComparisonProfileCell,
  ComparisonScreeningCell,
  ComparisonScreeningRow,
} from './types'

// FR-H14 bo cuc bang (UI.md 4a, >= md). <table> THUAN - khong TableRow shadcn (nen hover se de nen "Khac nhau",
// UI_GUIDE muc 6). Thu tu cot = thu tu columns backend tra (R-O1) - khong sap lai.
// Khac biet chi theo HANG (R-W2): hang co co valuesDiffer (backend tinh, frontend KHONG tu so gia tri) -> nen
// bg-m3-surface-container cho CA hang (ke ca o tieu de hang) + chip "Khac nhau" o tieu de hang; moi chu trong hang dung
// text-m3-on-surface. Khong className nao o day phu thuoc gia tri cua mot o (R-W1).
// thead khong dinh (sticky): dau cot cao (ten, badge, ngay, hai nut) se che phan lon man hinh md - UI.md 4a cho bo.

const CELL_CLASS = 'px-3 py-3 align-top text-m3-body-md text-m3-on-surface break-words'

function rowBackground(diff: boolean): string {
  return diff ? 'bg-m3-surface-container' : 'bg-m3-surface'
}

function secondaryText(onDiffRow: boolean): string {
  return onDiffRow ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'
}

// ---- Gia tri o dung chung voi ComparisonStacked (cung chu, cung kieu o hai bo cuc) ----

// "Chua co du lieu" - on-surface-variant tren nen surface; trong hang "Khac nhau" doi sang on-surface (UI.md 5d).
export function MissingValue({ onDiffRow = false }: { onDiffRow?: boolean }) {
  return <span className={secondaryText(onDiffRow)}>{MISSING_DATA}</span>
}

// R-D6 - ky nang nguyen van, du danh sach, noi bang " · " (khong cat "+3").
export function SkillsValue({ profile }: { profile: ComparisonProfileCell }) {
  if (!profile.parsedDataAvailable || profile.skills.length === 0) {
    return <MissingValue />
  }
  return <span>{profile.skills.join(' · ')}</span>
}

// R-D7 - moi muc hoc van mot dong, bo phan null, khong diem trung binh [Q10].
export function EducationValue({ profile }: { profile: ComparisonProfileCell }) {
  const lines = profile.parsedDataAvailable
    ? profile.education.map(formatEducation).filter((line) => line.length > 0)
    : []
  if (lines.length === 0) {
    return <MissingValue />
  }
  return (
    <ul className="flex flex-col gap-1">
      {lines.map((line, index) => (
        <li key={index}>{line}</li>
      ))}
    </ul>
  )
}

// Khoi noi dung AI (UI_GUIDE muc 4): nen surface-container-high + vien bat buoc, chu chi on-surface. Chi doc.
export function AiBlock({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface-container-high p-3 text-m3-body-md text-m3-on-surface">
      {children}
    </div>
  )
}

// Diem manh/yeu: "{criterionName} — {point}" (ExplanationReport.tsx:19); rong -> "Khong co muc nao.".
export function ExplanationPointList({ points }: { points: ExplanationPoint[] }) {
  if (points.length === 0) {
    return <p>{COMPARE_EXPLANATION_TEXT.emptyList}</p>
  }
  return (
    <ul className="flex flex-col gap-1">
      {points.map((point, index) => (
        <li key={`${point.criterionName}-${index}`}>{COMPARE_EXPLANATION_TEXT.point(point.criterionName, point.point)}</li>
      ))}
    </ul>
  )
}

// R-Q4 - o cau hoi sang loc; SHORT_TEXT them break-all cho chuoi lien khong khoang trang (UI.md muc 8).
export function ScreeningAnswerValue({
  row,
  cell,
  onDiffRow,
}: {
  row: ComparisonScreeningRow
  cell: ComparisonScreeningCell
  onDiffRow: boolean
}) {
  const text = screeningCellText(row.questionType, cell)
  if (cell.state !== 'ANSWERED') {
    return <span className={secondaryText(onDiffRow)}>{text}</span>
  }
  return <span className={row.questionType === 'SHORT_TEXT' ? 'break-all' : undefined}>{text}</span>
}

// Tieu de nhom (UI.md 5d) - hang <th colSpan scope="colgroup"> (UI.md muc 9).
function GroupHeaderRow({ colSpan, title, note, ai = false }: { colSpan: number; title: string; note?: string; ai?: boolean }) {
  return (
    <tr className="border-t border-m3-outline-variant bg-m3-surface">
      <th colSpan={colSpan} scope="colgroup" className="px-3 pt-5 pb-2 text-left font-normal">
        <div className="flex flex-col gap-1">
          <span className="flex flex-wrap items-center gap-2 text-m3-title-md text-m3-on-surface">
            {ai && (
              <span className="inline-flex items-center gap-1 text-m3-label-md text-m3-on-surface">
                <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
                {COMPARE_GROUP_TEXT.aiGenerated}
              </span>
            )}
            {title}
          </span>
          {note && <span className="text-m3-body-sm text-m3-on-surface-variant">{note}</span>}
        </div>
      </th>
    </tr>
  )
}

// Mot hang so sanh: tieu de hang <th scope="row"> + cac o. diff = co valuesDiffer cua backend (hoac false voi hang van
// ban khong co co).
function CompareRow({
  label,
  diff,
  extra,
  children,
}: {
  label: ReactNode
  diff: boolean
  extra?: ReactNode
  children: ReactNode
}) {
  return (
    <tr className={`border-t border-m3-outline-variant ${rowBackground(diff)}`}>
      <th scope="row" className="px-3 py-3 text-left align-top font-normal break-words">
        <div className="flex flex-col items-start gap-1">
          <span className="text-m3-label-lg text-m3-on-surface">{label}</span>
          {extra}
          {diff && <DiffMark />}
        </div>
      </th>
      {children}
    </tr>
  )
}

function criterionRegionId(baseId: string, rowIndex: number, columnIndex: number): string {
  return `${baseId}-criterion-${rowIndex}-${columnIndex}`
}

export function ComparisonTable({
  data,
  expandedKeys,
  onToggleCriterion,
}: {
  data: ApplicationComparisonResponse
  // Trang thai mo rong theo CriterionRow.key, giu o trang (khong theo bo cuc - UI.md muc 8).
  expandedKeys: ReadonlySet<string>
  onToggleCriterion: (key: string) => void
}) {
  const baseId = useId()
  const columnCount = data.columns.length
  const fullSpan = columnCount + 1
  const { screening } = data

  return (
    <div className="overflow-hidden rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface">
      <table className="w-full table-fixed border-collapse text-left">
        <caption className="sr-only">{COMPARE_PAGE_TEXT.tableCaption(columnCount, data.jobTitle)}</caption>
        <colgroup>
          <col className="w-32 lg:w-44" />
          {data.columns.map((column) => (
            <col key={column.applicationId} />
          ))}
        </colgroup>

        <thead>
          <tr className="bg-m3-surface">
            <td className="px-3 py-3" />
            {data.columns.map((column, index) => (
              <th key={column.applicationId} scope="col" className="px-3 py-3 text-left align-top font-normal">
                <ComparisonColumnHeader column={column} ordinal={index + 1} jobId={data.jobId} variant="table" />
              </th>
            ))}
          </tr>
        </thead>

        {/* Diem tong hop - R-D2, R-D3. Hai hang nay KHONG co nut mo rong (R-D13). */}
        <tbody>
          <GroupHeaderRow colSpan={fullSpan} title={COMPARE_GROUP_TEXT.summary} />
          <CompareRow label={COMPARE_ROW_TEXT.totalScore} diff={data.totalScore.valuesDiffer}>
            {data.totalScore.values.map((value, index) => (
              <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                {formatTotalScore(value)}
              </td>
            ))}
          </CompareRow>
          <CompareRow
            label={
              <span className="inline-flex items-center gap-1">
                {COMPARE_ROW_TEXT.rank}
                <Info className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              </span>
            }
            diff={data.rank.valuesDiffer}
            extra={
              <span className={`text-m3-body-sm ${secondaryText(data.rank.valuesDiffer)}`}>{COMPARE_ROW_TEXT.rankNote}</span>
            }
          >
            {data.rank.values.map((value, index) => (
              <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                {value}
              </td>
            ))}
          </CompareRow>
        </tbody>

        {/* Diem theo tieu chi - R-D4, R-C4, R-C5. Nut "Ly giai va trich dan" mo MOI o cua hang cung luc. */}
        <tbody>
          <GroupHeaderRow colSpan={fullSpan} title={COMPARE_GROUP_TEXT.criteria} />
          {data.criteria.map((row, rowIndex) => {
            const expanded = expandedKeys.has(row.key)
            const regionIds = row.cells
              .map((cell, columnIndex) => (cell === null ? null : criterionRegionId(baseId, rowIndex, columnIndex)))
              .filter((id): id is string => id !== null)
            return (
              <CompareRow
                key={row.key}
                label={row.criterionName}
                diff={row.valuesDiffer}
                extra={
                  <>
                    {row.rubricVersionsDiffer && (
                      <span className={`text-m3-body-sm ${secondaryText(row.valuesDiffer)}`}>
                        {COMPARE_CRITERION_TEXT.rowVersionsDiffer}
                      </span>
                    )}
                    {regionIds.length > 0 && (
                      <button
                        type="button"
                        aria-expanded={expanded}
                        aria-controls={regionIds.join(' ')}
                        onClick={() => onToggleCriterion(row.key)}
                        className="mt-1 inline-flex min-h-12 items-center gap-1 text-left text-m3-label-lg text-m3-on-surface hover:underline lg:min-h-0"
                      >
                        {expanded ? (
                          <ChevronDown className="h-4 w-4 shrink-0" aria-hidden="true" />
                        ) : (
                          <ChevronRight className="h-4 w-4 shrink-0" aria-hidden="true" />
                        )}
                        {COMPARE_CRITERION_TEXT.expand}
                      </button>
                    )}
                  </>
                }
              >
                {row.cells.map((cell, columnIndex) => (
                  <td key={data.columns[columnIndex].applicationId} className={CELL_CLASS}>
                    <CriterionCompareCell
                      cell={cell}
                      rowName={row.criterionName}
                      expanded={expanded}
                      regionId={criterionRegionId(baseId, rowIndex, columnIndex)}
                      onDiffRow={row.valuesDiffer}
                    />
                  </td>
                ))}
              </CompareRow>
            )
          })}
        </tbody>

        {/* Ho so tu CV - R-D5..R-D7. Chi kinh nghiem co co valuesDiffer; ky nang, hoc van la hang van ban. */}
        <tbody>
          <GroupHeaderRow colSpan={fullSpan} title={COMPARE_GROUP_TEXT.profile} note={COMPARE_GROUP_TEXT.profileNote} />
          <CompareRow label={COMPARE_ROW_TEXT.experience} diff={data.experience.valuesDiffer}>
            {data.experience.values.map((experience, index) => (
              <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                {experience.months === null || experience.years === null ? (
                  <MissingValue onDiffRow={data.experience.valuesDiffer} />
                ) : (
                  formatExperience(experience)
                )}
              </td>
            ))}
          </CompareRow>
          <CompareRow label={COMPARE_ROW_TEXT.skills} diff={false}>
            {data.profiles.map((profile, index) => (
              <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                <SkillsValue profile={profile} />
              </td>
            ))}
          </CompareRow>
          <CompareRow label={COMPARE_ROW_TEXT.education} diff={false}>
            {data.profiles.map((profile, index) => (
              <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                <EducationValue profile={profile} />
              </td>
            ))}
          </CompareRow>
        </tbody>

        {/* Tom tat diem manh, diem yeu - R-D8. Nhan "Do AI tao" MOT lan o tieu de nhom; moi o co giai thich nam trong
            khoi AI. Cot PENDING/FAILED: mot cau chu thuong gop doc qua ba hang (rowSpan). */}
        <tbody>
          <GroupHeaderRow colSpan={fullSpan} title={COMPARE_GROUP_TEXT.explanation} note={COMPARE_EXPLANATION_TEXT.note} ai />
          <CompareRow label={COMPARE_ROW_TEXT.summary} diff={false}>
            {data.explanations.map((cell, index) => {
              const statusText = explanationStatusText(cell)
              return statusText !== null ? (
                <td key={data.columns[index].applicationId} rowSpan={3} className={`${CELL_CLASS} bg-m3-surface`}>
                  <span className="text-m3-on-surface-variant">{statusText}</span>
                </td>
              ) : (
                <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                  <AiBlock>
                    <p className="whitespace-pre-wrap">{cell.summary}</p>
                  </AiBlock>
                </td>
              )
            })}
          </CompareRow>
          <CompareRow label={COMPARE_ROW_TEXT.strengths} diff={false}>
            {data.explanations.map((cell, index) =>
              explanationStatusText(cell) !== null ? null : (
                <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                  <AiBlock>
                    <ExplanationPointList points={cell.strengths} />
                  </AiBlock>
                </td>
              ),
            )}
          </CompareRow>
          <CompareRow label={COMPARE_ROW_TEXT.weaknesses} diff={false}>
            {data.explanations.map((cell, index) =>
              explanationStatusText(cell) !== null ? null : (
                <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                  <AiBlock>
                    <ExplanationPointList points={cell.weaknesses} />
                  </AiBlock>
                </td>
              ),
            )}
          </CompareRow>
        </tbody>

        {/* Cau tra loi sang loc - R-Q1..R-Q6. Cot NOT_CAPTURED/NO_QUESTIONS: MOT cau gop doc qua moi hang cau hoi
            (rowSpan). Khong con hang cau hoi nao (R-Q5): mot hang "Trang thai" duy nhat. */}
        <tbody>
          <GroupHeaderRow colSpan={fullSpan} title={COMPARE_GROUP_TEXT.screening} note={COMPARE_GROUP_TEXT.screeningNote} />
          {screening.rows.length === 0 ? (
            <CompareRow label={COMPARE_SCREENING_TEXT.statusRow} diff={false}>
              {screening.states.map((state, index) => (
                <td key={data.columns[index].applicationId} className={CELL_CLASS}>
                  <span className="text-m3-on-surface-variant">
                    {screeningStatusText(state, screening.capturedAt[index]) ?? MISSING_DATA}
                  </span>
                </td>
              ))}
            </CompareRow>
          ) : (
            screening.rows.map((row, rowIndex) => (
              <CompareRow
                key={rowIndex}
                label={row.questionText}
                diff={row.valuesDiffer}
                extra={
                  <span className={`text-m3-body-sm ${secondaryText(row.valuesDiffer)}`}>
                    {screeningQuestionLabel(row.questionType)}
                  </span>
                }
              >
                {row.cells.map((cell, columnIndex) => {
                  const statusText = screeningStatusText(
                    screening.states[columnIndex],
                    screening.capturedAt[columnIndex],
                  )
                  if (statusText !== null) {
                    // O gop doc: chi dat o hang dau, cac hang sau bo o cua cot nay.
                    return rowIndex === 0 ? (
                      <td
                        key={data.columns[columnIndex].applicationId}
                        rowSpan={screening.rows.length}
                        className={`${CELL_CLASS} bg-m3-surface`}
                      >
                        <span className="text-m3-on-surface-variant">{statusText}</span>
                      </td>
                    ) : null
                  }
                  return (
                    <td key={data.columns[columnIndex].applicationId} className={CELL_CLASS}>
                      <ScreeningAnswerValue row={row} cell={cell} onDiffRow={row.valuesDiffer} />
                    </td>
                  )
                })}
              </CompareRow>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
