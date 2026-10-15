import { useId } from 'react'
import { Info, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { CvQaCitationQuote } from './CvQaCitationQuote'
import { CV_QA_REFUSAL_LABELS, CV_QA_TEXT } from './cvQaLabels'
import type { CvQaResponse, CvQaSide, CvQaTurn } from './types'

// Cau truncatedNotice - chi trong khoi AI (ANSWERED/NOT_FOUND) va chi khi cvTruncated (UI.md muc 4a, 4b-2, 10).
// Nen surface-container-high -> chi dung on-surface.
function TruncatedNotice() {
  return (
    <p className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
      <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
      {CV_QA_TEXT.truncatedNotice}
    </p>
  )
}

// Khoi AI cho ANSWERED/NOT_FOUND: vien bat buoc (UI_GUIDE muc 4), nhan "Do AI tao" la chu that. Moi chu trong khoi
// chi text-m3-on-surface (UI.md muc 5d).
function AiBlock({ response }: { response: CvQaResponse }) {
  const answered = response.outcome === 'ANSWERED'
  return (
    <div className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3">
      <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
        <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
        {CV_QA_TEXT.aiBadge}
      </p>
      {/* Van ban thuan, giu xuong dong, khong HTML/Markdown. answer chi hien khi ANSWERED (UI.md muc 10). */}
      <p className="text-sm whitespace-pre-wrap break-words text-m3-on-surface">
        {answered ? response.answer : CV_QA_TEXT.notFound}
      </p>
      {response.cvTruncated && <TruncatedNotice />}
      {answered && response.citations.length > 0 && (
        <>
          <p className="text-sm break-words text-m3-on-surface">{CV_QA_TEXT.citationNote}</p>
          <p className="text-sm font-medium text-m3-on-surface">{CV_QA_TEXT.citationsHeading}</p>
          <ul className="flex flex-col gap-2">
            {response.citations.map((citation, index) => (
              <li key={index}>
                <CvQaCitationQuote quote={citation.quote} />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

// Khoi tu choi: trung tinh, KHONG nhan "Do AI tao" (ly do co the den tu bo chan tu khoa - UI.md muc 4b-3, 10). Lien ket
// U05 chi phia ung vien, chi ly do CV_IMPROVEMENT.
function RefusedBlock({
  response,
  side,
  resumeId,
}: {
  response: CvQaResponse
  side: CvQaSide
  resumeId?: string
}) {
  const reason = response.refusalReason
  const showCvImprovementLink = side === 'candidate' && reason === 'CV_IMPROVEMENT' && resumeId !== undefined
  return (
    <div className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3">
      {reason && (
        <p className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
          {CV_QA_REFUSAL_LABELS[reason]}
        </p>
      )}
      {showCvImprovementLink && (
        <Link
          to={`/candidate/resumes/${resumeId}/improvement-suggestions`}
          className="ml-5.5 text-sm text-m3-primary hover:underline"
        >
          {CV_QA_TEXT.refusedLinkCvImprovement}
        </Link>
      )}
    </div>
  )
}

// FR-C08 - mot luot: cau hoi + khoi theo outcome (UI.md muc 4a, 4b). Ba outcome khac nhau bang chu va khoi, khong bang
// mau (UI.md muc 9). Chi de doc - khong nut Dung/Luu/Sao chep (O9).
export function CvQaTurnItem({ turn, side, resumeId }: { turn: CvQaTurn; side: CvQaSide; resumeId?: string }) {
  const questionId = useId()
  const { response } = turn

  return (
    <article aria-labelledby={questionId} className="flex flex-col gap-2">
      <p className="text-xs font-medium text-m3-on-surface-variant">{CV_QA_TEXT.turnQuestionLabel}</p>
      <p id={questionId} className="text-sm whitespace-pre-wrap break-words text-m3-on-surface">
        {turn.question}
      </p>
      {response.outcome === 'REFUSED' ? (
        <RefusedBlock response={response} side={side} resumeId={resumeId} />
      ) : (
        <AiBlock response={response} />
      )}
    </article>
  )
}
