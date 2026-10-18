import { Info, SlidersHorizontal, Sparkles, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useCatalogsQuery } from '../catalog/queries'
import type { CatalogItem } from '../catalog/types'
import { WORK_MODE_LABELS } from '../jobs/jobLabels'
import {
  chipEditAria,
  chipRemoveAria,
  descriptionLine,
  JOB_SEARCH_TEXT,
  salaryConditionLabel,
} from './jobSearchLabels'
import type { JobSearchCondition, JobSearchInterpretation, JobSearchRanking } from './types'

interface ConditionChip {
  key: string
  text: string
  condition: JobSearchCondition
}

// Ma khong con trong danh muc (hoac danh muc chua tai/loi) -> hien chinh ma (UI.md muc 5a).
function catalogText(items: CatalogItem[] | undefined, code: string): string {
  return items?.find((item) => item.code === code)?.label ?? code
}

interface InterpretationPanelProps {
  interpretation: JobSearchInterpretation
  ranking: JobSearchRanking
  // removed = the vua bam xoa; khong truyen = sua (giu moi dieu kien) - Q-T5.
  onEditConditions: (removed?: JobSearchCondition) => void
}

// FR-U13 UI.md muc 4a, 5a, 5d, 6, 9 - khoi AI "He thong hieu yeu cau cua ban". Vien bat buoc; chu trong khoi CHI
// text-m3-on-surface (on-surface-variant khong dat tren surface-container-high). Mot kieu chip trung tinh cho moi loai
// dieu kien - khong mau theo loai hay theo muc khop. Thu tu the: khu vuc, nganh, luong, hinh thuc.
export function InterpretationPanel({ interpretation, ranking, onEditConditions }: InterpretationPanelProps) {
  const catalogsQuery = useCatalogsQuery()

  const chips: ConditionChip[] = [
    ...interpretation.locationCodes.map((code) => ({
      key: `location-${code}`,
      text: catalogText(catalogsQuery.data?.provinces, code),
      condition: { kind: 'location', code } as const,
    })),
    ...interpretation.industryCodes.map((code) => ({
      key: `industry-${code}`,
      text: catalogText(catalogsQuery.data?.industries, code),
      condition: { kind: 'industry', code } as const,
    })),
  ]
  const salaryText = salaryConditionLabel(interpretation.salaryMin, interpretation.salaryMax)
  if (salaryText !== null) {
    chips.push({ key: 'salary', text: salaryText, condition: { kind: 'salary' } })
  }
  for (const mode of interpretation.workModes) {
    chips.push({ key: `workMode-${mode}`, text: WORK_MODE_LABELS[mode] ?? mode, condition: { kind: 'workMode', code: mode } })
  }

  return (
    <section className="flex flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-4">
      <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
        <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
        {JOB_SEARCH_TEXT.aiBadge}
      </p>
      <h3 className="text-m3-title-md text-m3-on-surface">{JOB_SEARCH_TEXT.interpretationTitle}</h3>

      {chips.length > 0 ? (
        <ul className="flex flex-wrap gap-2">
          {chips.map((chip) => (
            <li
              key={chip.key}
              className="inline-flex min-h-8 items-center gap-1 rounded-m3-sm border border-m3-outline bg-m3-surface pl-3 text-sm text-m3-on-surface"
            >
              <button
                type="button"
                aria-label={chipEditAria(chip.text)}
                onClick={() => onEditConditions()}
                className="min-h-8 break-words text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
              >
                {chip.text}
              </button>
              <button
                type="button"
                aria-label={chipRemoveAria(chip.text)}
                onClick={() => onEditConditions(chip.condition)}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-m3-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-m3-on-surface">{JOB_SEARCH_TEXT.nlNoConditions}</p>
      )}

      {ranking === 'NEWEST' ? (
        <p className="text-sm text-m3-on-surface">{JOB_SEARCH_TEXT.nlNoDescription}</p>
      ) : (
        interpretation.description !== null && (
          <p className="text-sm break-words text-m3-on-surface">{descriptionLine(interpretation.description)}</p>
        )
      )}

      {interpretation.personalInfoIgnored && (
        <p className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          {JOB_SEARCH_TEXT.nlPersonalInfoIgnored}
        </p>
      )}

      {/* Cau nhac chi co nghia khi co the de bam (L42). */}
      {chips.length > 0 && <p className="text-sm text-m3-on-surface">{JOB_SEARCH_TEXT.interpretationCaution}</p>}

      <div className="flex sm:justify-end">
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="min-h-12 w-full sm:min-h-10 sm:w-auto"
          onClick={() => onEditConditions()}
        >
          <SlidersHorizontal aria-hidden="true" />
          {JOB_SEARCH_TEXT.editConditions}
        </Button>
      </div>
    </section>
  )
}
