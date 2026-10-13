import { Sparkles } from 'lucide-react'
import { JOB_DRAFT_TEXT } from './jobDraftLabels'

// UI.md 4d, 5d - dau "Do AI goi y" canh nhan truong/the. La chu that (khong chi icon); o nhap gan vao bang
// aria-describedby={id} de trinh doc man hinh doc khi focus o.
export function AiSuggestedMark({ id }: { id?: string }) {
  return (
    <span id={id} className="inline-flex items-center gap-1 text-m3-label-md text-m3-on-surface-variant">
      <Sparkles className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {JOB_DRAFT_TEXT.aiSuggested}
    </span>
  )
}
