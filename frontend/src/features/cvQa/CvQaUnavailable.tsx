import { Link } from 'react-router-dom'
import type { ParseStatus } from '../resumes/types'
import { CV_QA_TEXT } from './cvQaLabels'
import type { CvQaSide } from './types'

const UNAVAILABLE_TEXT: Record<CvQaSide, { pending: string; failed: string }> = {
  hr: { pending: CV_QA_TEXT.unavailablePendingHr, failed: CV_QA_TEXT.unavailableFailedHr },
  candidate: { pending: CV_QA_TEXT.unavailablePendingCandidate, failed: CV_QA_TEXT.unavailableFailedCandidate },
}

// FR-C08 - khoi 4d: CV chua hoi duoc, thay CA khoi hoi dap (khong o hoi, khong nut). Chi render khi parseStatus khac DONE.
export function CvQaUnavailable({ side, parseStatus }: { side: CvQaSide; parseStatus: ParseStatus }) {
  const text = parseStatus === 'FAILED' ? UNAVAILABLE_TEXT[side].failed : UNAVAILABLE_TEXT[side].pending

  return (
    <div className="flex flex-col items-start gap-2">
      <p className="text-sm break-words text-m3-on-surface">{text}</p>
      {side === 'candidate' && (
        <Link to="/candidate/profile" className="text-sm text-m3-primary hover:underline">
          {CV_QA_TEXT.backToProfile}
        </Link>
      )}
    </div>
  )
}
