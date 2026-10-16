import { ChevronDown, ChevronRight, Info, Sparkles } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { formatTotalScore } from '../../lib/score'
import { ComparisonColumnHeader } from './ComparisonColumnHeader'
import {
  AiBlock,
  EducationValue,
  ExplanationPointList,
  MissingValue,
  ScreeningAnswerValue,
  SkillsValue,
} from './ComparisonTable'
import {
  COMPARE_COLUMN_TEXT,
  COMPARE_CRITERION_TEXT,
  COMPARE_EXPLANATION_TEXT,
  COMPARE_GROUP_TEXT,
  COMPARE_ROW_TEXT,
  COMPARE_SCREENING_TEXT,
  explanationStatusText,
  formatExperience,
  MISSING_DATA,
  screeningQuestionLabel,
  screeningStatusText,
} from './comparisonLabels'
import { CriterionCompareCell } from './CriterionCompareCell'
import { DiffMark } from './DiffMark'
import type { ApplicationComparisonResponse } from './types'

// FR-H14 bo cuc theo hang (UI.md 4c, < md, gom 375px): KHONG bang. Moi hang so sanh la mot khoi <section aria-labelledby>,
// trong khoi la <dl> liet ke gia tri tung ung vien theo thu tu cot ("{i} · {ten}"). Khoi co co valuesDiffer: nen
// bg-m3-surface-container phu CA khoi + chip "Khac nhau" o tieu de khoi, chu chi text-m3-on-surface (R-W2). Khong cuon
// ngang: moi gia tri break-words.

function blockBackground(diff: boolean): string {
  return diff ? 'bg-m3-surface-container' : 'bg-m3-surface'
}

function GroupHeading({ id, title, note, ai = false }: { id?: string; title: string; note?: string; ai?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      {ai && (
        <span className="inline-flex items-center gap-1 text-m3-label-md text-m3-on-surface">
          <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
          {COMPARE_GROUP_TEXT.aiGenerated}
        </span>
      )}
      <h2 id={id} className="text-m3-title-md text-m3-on-surface">
        {title}
      </h2>
      {note && <p className="text-m3-body-sm text-m3-on-surface-variant">{note}</p>}
    </div>
  )
}

function CompareBlock({
  label,
  diff,
  extra,
  footer,
  children,
}: {
  label: ReactNode
  diff: boolean
  extra?: ReactNode
  footer?: ReactNode
  children: ReactNode
}) {
  const headingId = useId()
  return (
    <section
      aria-labelledby={headingId}
      className={`rounded-(--radius-card) border border-m3-outline-variant p-4 ${blockBackground(diff)}`}
    >
      <div className="flex flex-col items-start gap-1 break-words">
        <h3 id={headingId} className="text-m3-label-lg text-m3-on-surface">
          {label}
        </h3>
        {extra}
        {diff && <DiffMark />}
      </div>
      <dl className="mt-3 flex flex-col gap-3">{children}</dl>
      {footer}
    </section>
  )
}

// Mot ung vien trong khoi: <dt> "{i} · {ten}", <dd> gia tri (thut le nhu 4c).
function Entry({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-m3-body-sm text-m3-on-surface break-words">{term}</dt>
      <dd className="mt-0.5 pl-4 text-m3-body-md text-m3-on-surface break-words">{children}</dd>
    </div>
  )
}

export function ComparisonStacked({
  data,
  expandedKeys,
  onToggleCriterion,
}: {
  data: ApplicationComparisonResponse
  // Cung state voi ComparisonTable (giu o trang theo CriterionRow.key) - doi qua nguong md khong mat trang thai mo.
  expandedKeys: ReadonlySet<string>
  onToggleCriterion: (key: string) => void
}) {
  const baseId = useId()
  const explanationHeadingId = useId()
  const terms = data.columns.map((column, index) => COMPARE_COLUMN_TEXT.ordinal(index + 1, column.candidateName))
  const { screening } = data
  const screeningStatuses = screening.states.map((state, index) => screeningStatusText(state, screening.capturedAt[index]))
  const hasStatusColumn = screeningStatuses.some((text) => text !== null)

  return (
    <div className="flex min-w-0 flex-col gap-6">
      {/* The ung vien = dau cot cua 4a. */}
      <div className="flex flex-col gap-3">
        {data.columns.map((column, index) => (
          <div
            key={column.applicationId}
            className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4"
          >
            <ComparisonColumnHeader column={column} ordinal={index + 1} jobId={data.jobId} variant="card" />
          </div>
        ))}
      </div>

      {/* Diem tong hop - khong nut mo rong (R-D13). */}
      <div className="flex flex-col gap-3">
        <GroupHeading title={COMPARE_GROUP_TEXT.summary} />
        <CompareBlock label={COMPARE_ROW_TEXT.totalScore} diff={data.totalScore.valuesDiffer}>
          {data.totalScore.values.map((value, index) => (
            <Entry key={data.columns[index].applicationId} term={terms[index]}>
              {formatTotalScore(value)}
            </Entry>
          ))}
        </CompareBlock>
        <CompareBlock
          label={
            <span className="inline-flex items-center gap-1">
              {COMPARE_ROW_TEXT.rank}
              <Info className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            </span>
          }
          diff={data.rank.valuesDiffer}
          extra={
            <p
              className={`text-m3-body-sm ${data.rank.valuesDiffer ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'}`}
            >
              {COMPARE_ROW_TEXT.rankNote}
            </p>
          }
        >
          {data.rank.values.map((value, index) => (
            <Entry key={data.columns[index].applicationId} term={terms[index]}>
              {value}
            </Entry>
          ))}
        </CompareBlock>
      </div>

      {/* Diem theo tieu chi - mo rong: ly giai + evidence hien duoi TUNG ung vien trong khoi, theo thu tu cot. */}
      <div className="flex flex-col gap-3">
        <GroupHeading title={COMPARE_GROUP_TEXT.criteria} />
        {data.criteria.map((row, rowIndex) => {
          const expanded = expandedKeys.has(row.key)
          const regionId = (columnIndex: number) => `${baseId}-criterion-${rowIndex}-${columnIndex}`
          const regionIds = row.cells
            .map((cell, columnIndex) => (cell === null ? null : regionId(columnIndex)))
            .filter((id): id is string => id !== null)
          return (
            <CompareBlock
              key={row.key}
              label={row.criterionName}
              diff={row.valuesDiffer}
              extra={
                row.rubricVersionsDiffer && (
                  <p
                    className={`text-m3-body-sm ${row.valuesDiffer ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'}`}
                  >
                    {COMPARE_CRITERION_TEXT.rowVersionsDiffer}
                  </p>
                )
              }
              footer={
                regionIds.length > 0 && (
                  <button
                    type="button"
                    aria-expanded={expanded}
                    aria-controls={regionIds.join(' ')}
                    onClick={() => onToggleCriterion(row.key)}
                    className="mt-3 flex min-h-12 w-full items-center justify-center gap-1 rounded-m3-button border border-m3-outline px-3 text-m3-label-lg text-m3-on-surface"
                  >
                    {expanded ? (
                      <ChevronDown className="h-4 w-4 shrink-0" aria-hidden="true" />
                    ) : (
                      <ChevronRight className="h-4 w-4 shrink-0" aria-hidden="true" />
                    )}
                    {COMPARE_CRITERION_TEXT.expand}
                  </button>
                )
              }
            >
              {row.cells.map((cell, columnIndex) => (
                <Entry key={data.columns[columnIndex].applicationId} term={terms[columnIndex]}>
                  <CriterionCompareCell
                    cell={cell}
                    rowName={row.criterionName}
                    expanded={expanded}
                    regionId={regionId(columnIndex)}
                    onDiffRow={row.valuesDiffer}
                    inline
                  />
                </Entry>
              ))}
            </CompareBlock>
          )
        })}
      </div>

      {/* Ho so tu CV - chi kinh nghiem co co valuesDiffer. */}
      <div className="flex flex-col gap-3">
        <GroupHeading title={COMPARE_GROUP_TEXT.profile} note={COMPARE_GROUP_TEXT.profileNote} />
        <CompareBlock label={COMPARE_ROW_TEXT.experience} diff={data.experience.valuesDiffer}>
          {data.experience.values.map((experience, index) => (
            <Entry key={data.columns[index].applicationId} term={terms[index]}>
              {experience.months === null || experience.years === null ? (
                <MissingValue onDiffRow={data.experience.valuesDiffer} />
              ) : (
                formatExperience(experience)
              )}
            </Entry>
          ))}
        </CompareBlock>
        <CompareBlock label={COMPARE_ROW_TEXT.skills} diff={false}>
          {data.profiles.map((profile, index) => (
            <Entry key={data.columns[index].applicationId} term={terms[index]}>
              <SkillsValue profile={profile} />
            </Entry>
          ))}
        </CompareBlock>
        <CompareBlock label={COMPARE_ROW_TEXT.education} diff={false}>
          {data.profiles.map((profile, index) => (
            <Entry key={data.columns[index].applicationId} term={terms[index]}>
              <EducationValue profile={profile} />
            </Entry>
          ))}
        </CompareBlock>
      </div>

      {/* Tom tat diem manh, diem yeu - moi ung vien MOT khoi AI (tom tat + diem manh + diem can cai thien); PENDING/
          FAILED la chu thuong, khong khoi AI. Khong metCriteria/missingCriteria (R-W4). */}
      <section aria-labelledby={explanationHeadingId} className="flex flex-col gap-3">
        <GroupHeading
          id={explanationHeadingId}
          title={COMPARE_GROUP_TEXT.explanation}
          note={COMPARE_EXPLANATION_TEXT.note}
          ai
        />
        <dl className="flex flex-col gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4">
          {data.explanations.map((cell, index) => {
            const statusText = explanationStatusText(cell)
            return (
              <Entry key={data.columns[index].applicationId} term={terms[index]}>
                {statusText !== null ? (
                  <span className="text-m3-on-surface-variant">{statusText}</span>
                ) : (
                  <AiBlock>
                    <div className="flex flex-col gap-3">
                      <div>
                        <h4 className="text-m3-label-lg">{COMPARE_ROW_TEXT.summary}</h4>
                        <p className="mt-1 whitespace-pre-wrap">{cell.summary}</p>
                      </div>
                      <div>
                        <h4 className="text-m3-label-lg">{COMPARE_ROW_TEXT.strengths}</h4>
                        <div className="mt-1">
                          <ExplanationPointList points={cell.strengths} />
                        </div>
                      </div>
                      <div>
                        <h4 className="text-m3-label-lg">{COMPARE_ROW_TEXT.weaknesses}</h4>
                        <div className="mt-1">
                          <ExplanationPointList points={cell.weaknesses} />
                        </div>
                      </div>
                    </div>
                  </AiBlock>
                )}
              </Entry>
            )
          })}
        </dl>
      </section>

      {/* Cau tra loi sang loc - don NOT_CAPTURED/NO_QUESTIONS: cau DAY DU o khoi "Trang thai" dau nhom (R-Q4). Moi khoi
          cau hoi van liet ke DU moi cot theo thu tu columns (UI.md 4c, lam ro dot 5): o cua cot do dung cau ngan.
          Khong con hang cau hoi nao (R-Q5): chi khoi "Trang thai". */}
      <div className="flex flex-col gap-3">
        <GroupHeading title={COMPARE_GROUP_TEXT.screening} note={COMPARE_GROUP_TEXT.screeningNote} />
        {(hasStatusColumn || screening.rows.length === 0) && (
          <CompareBlock label={COMPARE_SCREENING_TEXT.statusRow} diff={false}>
            {screeningStatuses.map((text, index) =>
              text === null && screening.rows.length > 0 ? null : (
                <Entry key={data.columns[index].applicationId} term={terms[index]}>
                  <span className="text-m3-on-surface-variant">{text ?? MISSING_DATA}</span>
                </Entry>
              ),
            )}
          </CompareBlock>
        )}
        {screening.rows.map((row, rowIndex) => (
          <CompareBlock
            key={rowIndex}
            label={row.questionText}
            diff={row.valuesDiffer}
            extra={
              <p
                className={`text-m3-body-sm ${row.valuesDiffer ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'}`}
              >
                {screeningQuestionLabel(row.questionType)}
              </p>
            }
          >
            {row.cells.map((cell, columnIndex) => {
              const state = screening.states[columnIndex]
              return (
                <Entry key={data.columns[columnIndex].applicationId} term={terms[columnIndex]}>
                  {state === 'NOT_CAPTURED' || state === 'NO_QUESTIONS' ? (
                    <span className={row.valuesDiffer ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'}>
                      {state === 'NOT_CAPTURED' ? COMPARE_SCREENING_TEXT.notCapturedShort : COMPARE_SCREENING_TEXT.notAsked}
                    </span>
                  ) : (
                    <ScreeningAnswerValue row={row} cell={cell} onDiffRow={row.valuesDiffer} />
                  )}
                </Entry>
              )
            })}
          </CompareBlock>
        ))}
      </div>
    </div>
  )
}
