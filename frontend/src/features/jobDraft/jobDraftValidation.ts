import {
  OPTIONS_MAX,
  OPTIONS_MIN,
  OPTION_TEXT_MAX,
  QUESTION_TEXT_MAX,
  SCREENING_FORM_ERRORS,
  countChars,
} from '../screening/screeningLabels'
import type { ScreeningQuestionType } from '../screening/types'
import { CRITERION_NAME_MAX, JOB_DRAFT_TEXT } from './jobDraftLabels'
import type { KeptCriterion } from './useJobAiDraft'

// FR-H11 - kiem phia trinh duyet cho tieu chi/cau hoi da giu (backend J2 van la chot chan - R-W3, R-L4). Tach khoi file
// component (react-refresh chi cho file component export component).

// O trong so (R-W2, R-W3 - cung bien 0.01..100 cua FR-H03). null = hop le.
export function weightError(value: string): string | null {
  if (value.trim() === '') {
    return JOB_DRAFT_TEXT.weightRequired
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0.01 || parsed > 100) {
    return JOB_DRAFT_TEXT.weightRange
  }
  return null
}

// Ten tieu chi: bat buoc, <= 200 ky tu sau khi bo khoang trang, khong trung (so nguyen van sau khi bo khoang trang -
// khop rang buoc DB cua FR-H03).
export function criterionNameError(name: string, id: string, kept: KeptCriterion[]): string | null {
  const trimmed = name.trim()
  if (trimmed.length === 0) {
    return JOB_DRAFT_TEXT.criterionNameRequired
  }
  if (trimmed.length > CRITERION_NAME_MAX) {
    return JOB_DRAFT_TEXT.criterionNameTooLong
  }
  if (kept.some((other) => other.id !== id && other.name.trim() === trimmed)) {
    return JOB_DRAFT_TEXT.criterionNameDuplicate
  }
  return null
}

// Tong cac o trong so dang hop le (o trong/sai khong tinh).
export function totalWeight(kept: KeptCriterion[]): number {
  return kept.reduce((sum, criterion) => (weightError(criterion.weight) ? sum : sum + Number(criterion.weight)), 0)
}

export function weightInputId(id: string): string {
  return `job-draft-weight-${id}`
}

export interface QuestionEditErrors {
  text?: string
  options?: string
  optionItems: Record<number, string>
}

// Cung gioi han va cau loi cua FR-H10 (screeningLabels.ts) - dem code point sau khi bo khoang trang (R-Q4b).
export function questionEditErrors(
  type: ScreeningQuestionType,
  text: string,
  options: string[],
): QuestionEditErrors | null {
  const errors: QuestionEditErrors = { optionItems: {} }
  const length = countChars(text)
  if (length === 0) {
    errors.text = SCREENING_FORM_ERRORS.textEmpty
  } else if (length > QUESTION_TEXT_MAX) {
    errors.text = SCREENING_FORM_ERRORS.textTooLong
  }
  if (type === 'SINGLE_CHOICE') {
    if (options.length < OPTIONS_MIN) {
      errors.options = SCREENING_FORM_ERRORS.optionsTooFew
    } else if (options.length > OPTIONS_MAX) {
      errors.options = SCREENING_FORM_ERRORS.optionsTooMany
    }
    const seen = new Set<string>()
    options.forEach((option, index) => {
      const trimmed = option.trim()
      if (trimmed.length === 0) {
        errors.optionItems[index] = SCREENING_FORM_ERRORS.optionEmpty
        return
      }
      if (countChars(trimmed) > OPTION_TEXT_MAX) {
        errors.optionItems[index] = SCREENING_FORM_ERRORS.optionTooLong
      }
      const key = trimmed.toLowerCase()
      if (seen.has(key) && !errors.options) {
        errors.options = SCREENING_FORM_ERRORS.optionDuplicate
      }
      seen.add(key)
    })
  }
  const hasError = Boolean(errors.text || errors.options || Object.keys(errors.optionItems).length > 0)
  return hasError ? errors : null
}

export function questionCardId(id: string): string {
  return `job-draft-question-${id}`
}

// Focus o/the sau khi React da render buoc/the moi (goi tu event handler - lan commit chay truoc callback nay).
export function focusSoon(elementId: string | undefined) {
  if (!elementId) {
    return
  }
  window.setTimeout(() => document.getElementById(elementId)?.focus(), 0)
}
