import { Sparkles } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { formatDeadline } from '../../lib/date'
import type { CatalogItem } from '../catalog/types'
import { formatSalary } from '../jobs/formatSalary'
import { EMPLOYMENT_TYPE_LABELS, WORK_MODE_LABELS } from '../jobs/jobLabels'
import { JOB_DRAFT_TEXT } from './jobDraftLabels'
import type { JobDraft } from './types'

// UI.md 4d - khoi "Do AI tao · Ban nhap tin": xem truoc, CHUA co hieu luc. Gia tri null hien "Chua co du lieu" (khong an
// dong). Van ban thuan (khong HTML/Markdown). Chu CHI text-m3-on-surface (UI.md 5d). Sau khi dien: mot dong thu gon.
export function PostingSuggestionBlock({
  draft,
  filled,
  industries,
  provinces,
  onFill,
  onRegenerate,
  regenerateDisabled,
}: {
  draft: JobDraft
  filled: boolean
  industries: CatalogItem[] | undefined
  provinces: CatalogItem[] | undefined
  onFill: () => void
  onRegenerate: () => void
  regenerateDisabled: boolean
}) {
  const regenerateButton = (
    <Button
      type="button"
      variant="outline"
      className="min-h-12 w-full sm:min-h-10 sm:w-auto"
      disabled={regenerateDisabled}
      onClick={onRegenerate}
    >
      {JOB_DRAFT_TEXT.regenerate}
    </Button>
  )

  if (filled) {
    return (
      <div className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
          <Sparkles className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          {JOB_DRAFT_TEXT.filled}
        </p>
        {regenerateButton}
      </div>
    )
  }

  const rows = JOB_DRAFT_TEXT.previewRows
  const labelOf = (items: CatalogItem[] | undefined, code: string | null) =>
    code == null ? null : (items?.find((item) => item.code === code)?.label ?? code)
  const salary = formatSalary(draft)

  return (
    <div className="flex flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3 sm:p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
          <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
          {JOB_DRAFT_TEXT.aiBlockLabel} · {JOB_DRAFT_TEXT.previewTitle}
        </p>
        {regenerateButton}
      </div>
      <dl className="flex flex-col gap-1.5 text-sm text-m3-on-surface">
        <PreviewRow label={rows.title} value={draft.title} />
        <PreviewRow label={rows.category} value={labelOf(industries, draft.categoryCode)} />
        <PreviewRow label={rows.location} value={labelOf(provinces, draft.locationCode)} />
        <PreviewRow
          label={rows.employmentType}
          value={draft.employmentType ? (EMPLOYMENT_TYPE_LABELS[draft.employmentType] ?? draft.employmentType) : null}
        />
        <PreviewRow
          label={rows.workMode}
          value={draft.workMode ? (WORK_MODE_LABELS[draft.workMode] ?? draft.workMode) : null}
        />
        <PreviewRow label={rows.salary} value={salary} />
        <PreviewRow label={rows.deadline} value={draft.deadline ? formatDeadline(draft.deadline) : null} />
        <PreviewRow label={rows.description} value={draft.description} long />
        <PreviewRow label={rows.requirements} value={draft.requirements} long />
        <PreviewRow label={rows.benefits} value={draft.benefits} long />
      </dl>
      <div className="flex sm:justify-end">
        <Button type="button" className="min-h-12 w-full sm:min-h-10 sm:w-auto" onClick={onFill}>
          {JOB_DRAFT_TEXT.fill}
        </Button>
      </div>
    </div>
  )
}

// Van ban dai hien toi da 3 dong + "Xem day du" (mo rong tai cho).
function PreviewRow({ label, value, long = false }: { label: string; value: string | null; long?: boolean }) {
  const [expanded, setExpanded] = useState(false)
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
      <dt className="shrink-0 font-medium sm:w-32">{label}</dt>
      <dd className="min-w-0 flex-1">
        {value == null ? (
          <span>{JOB_DRAFT_TEXT.noData}</span>
        ) : long ? (
          <div className="flex flex-col items-start gap-1">
            <p className={`whitespace-pre-wrap break-words ${expanded ? '' : 'line-clamp-3'}`}>{value}</p>
            <button
              type="button"
              className="min-h-10 text-sm text-m3-on-surface underline underline-offset-2"
              aria-expanded={expanded}
              onClick={() => setExpanded((current) => !current)}
            >
              {expanded ? JOB_DRAFT_TEXT.showLess : JOB_DRAFT_TEXT.showMore}
            </button>
          </div>
        ) : (
          <span className="break-words">{value}</span>
        )}
      </dd>
    </div>
  )
}
