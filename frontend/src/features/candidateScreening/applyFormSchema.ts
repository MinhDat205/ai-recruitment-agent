import { z } from 'zod'
import type { ScreeningQuestionResponse } from '../screening/types'
import { ANSWER_TEXT_MAX, CANDIDATE_SCREENING_ERRORS } from './candidateScreeningLabels'
import type { ScreeningAnswerInput } from './types'

// Gia tri cua o YES_NO trong form (radio chi giu chuoi); doi sang boolean khi gui (toAnswerInputs).
export const YES = 'yes'
export const NO = 'no'

// answers: khoa = id cau hoi. SHORT_TEXT: chu da go; SINGLE_CHOICE: chu cua phuong an; YES_NO: YES/NO. Chua chon /
// "Bo chon" = '' hoac null/undefined (radio chua tick tra null) - moi noi doc deu quy ve '' bang answerOf.
export const applyFormBaseSchema = z.object({
  resumeId: z.string(),
  coverLetter: z.string(),
  aiConsent: z.boolean(),
  talentPoolConsent: z.boolean(),
  answers: z.record(z.string(), z.string().nullish()),
})

export type ApplyFormValues = z.infer<typeof applyFormBaseSchema>
export type ApplyAnswers = ApplyFormValues['answers']

export function answerOf(answers: ApplyAnswers, questionId: string): string {
  return answers[questionId] ?? ''
}

// Dem ky tu theo code point SAU khi bo khoang trang hai dau - khop backend (strip + codePointCount, R-V1).
export function countAnswerChars(value: string): number {
  return [...value.trim()].length
}

// Schema dung lai moi khi bo cau hoi doi (tai lan dau, tai lai sau 409). Kiem DUNG nhu R-V2 cua backend tren tung cau
// theo thu tu hien thi; backend van la chot chan (400 INVALID_SCREENING_ANSWER).
//   - Cau bat buoc phai co cau tra loi (SHORT_TEXT: khac rong sau trim).
//   - SHORT_TEXT toi da ANSWER_TEXT_MAX code point sau trim.
//   - SINGLE_CHOICE chi nhan dung mot phan tu cua options; YES_NO chi nhan YES/NO.
// aiConsent khong kiem o day: nut Nop khoa toi khi tick (UI_GUIDE muc 4), backend chan bang @AssertTrue.
export function buildApplyFormSchema(questions: readonly ScreeningQuestionResponse[]) {
  return applyFormBaseSchema.superRefine((values, ctx) => {
    for (const question of questions) {
      const path = ['answers', question.id]
      const raw = answerOf(values.answers, question.id)

      if (question.questionType === 'SHORT_TEXT') {
        const length = countAnswerChars(raw)
        if (length === 0) {
          if (question.required) {
            ctx.addIssue({ code: 'custom', path, message: CANDIDATE_SCREENING_ERRORS.required })
          }
        } else if (length > ANSWER_TEXT_MAX) {
          ctx.addIssue({ code: 'custom', path, message: CANDIDATE_SCREENING_ERRORS.tooLong })
        }
        continue
      }

      if (raw === '') {
        if (question.required) {
          ctx.addIssue({ code: 'custom', path, message: CANDIDATE_SCREENING_ERRORS.required })
        }
        continue
      }
      const allowed = question.questionType === 'SINGLE_CHOICE' ? question.options : [YES, NO]
      if (!allowed.includes(raw)) {
        ctx.addIssue({ code: 'custom', path, message: CANDIDATE_SCREENING_ERRORS.optionNotFound })
      }
    }
  })
}

// R-F4 - chi gui phan tu cho cau DA tra loi; cau khong bat buoc bo trong thi khong gui (backend ghi dong voi NULL).
// SHORT_TEXT gui gia tri da trim; SINGLE_CHOICE gui nguyen chu phuong an.
export function toAnswerInputs(
  questions: readonly ScreeningQuestionResponse[],
  answers: ApplyAnswers,
): ScreeningAnswerInput[] {
  const inputs: ScreeningAnswerInput[] = []
  for (const question of questions) {
    const raw = answerOf(answers, question.id)
    if (question.questionType === 'YES_NO') {
      if (raw === YES || raw === NO) {
        inputs.push({ questionId: question.id, answerText: null, answerYesNo: raw === YES })
      }
      continue
    }
    const text = question.questionType === 'SHORT_TEXT' ? raw.trim() : raw
    if (text !== '') {
      inputs.push({ questionId: question.id, answerText: text, answerYesNo: null })
    }
  }
  return inputs
}

// R-F5 - sau 409 SCREENING_QUESTIONS_CHANGED: giu cau tra loi CHI KHI cau moi co cung id VA cung loai voi cau cu;
// SINGLE_CHOICE con them dieu kien chu phuong an da chon van con trong options moi (so khop dung tung ky tu). Moi cau
// tra loi khac bi xoa (cau da bi xoa khoi tin khong con khoa trong ket qua).
export function mergeAnswersAfterReload(
  previousQuestions: readonly ScreeningQuestionResponse[],
  nextQuestions: readonly ScreeningQuestionResponse[],
  answers: ApplyAnswers,
): ApplyAnswers {
  const previousById = new Map(previousQuestions.map((question) => [question.id, question]))
  const merged: ApplyAnswers = {}
  for (const question of nextQuestions) {
    const previous = previousById.get(question.id)
    const raw = answerOf(answers, question.id)
    const keep =
      previous !== undefined &&
      previous.questionType === question.questionType &&
      (question.questionType !== 'SINGLE_CHOICE' || question.options.includes(raw))
    merged[question.id] = keep ? raw : ''
  }
  return merged
}
