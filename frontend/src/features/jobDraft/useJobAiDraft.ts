import { isAxiosError } from 'axios'
import { useCallback, useRef, useState } from 'react'
import { extractErrorMessage } from '../../lib/httpError'
import type { ScreeningQuestionType } from '../screening/types'
import { JOB_DRAFT_TEXT, MAX_KEPT_CRITERIA, MAX_KEPT_QUESTIONS, type FillableField } from './jobDraftLabels'
import { useGenerateJobDraftMutation } from './queries'
import type { CriteriaDraft, JobAiDraftResponse, JobDraft, JobDraftPart, PartResult, QuestionsDraft } from './types'

// FR-H11 UI.md 5a - state che do AI cua trang tao tin. CHI state React: khong luu vao bo nho trinh duyet hay URL, tai lai
// trang = mat het (UI.md muc 3). Tai mot thoi diem chi MOT request J1 (isGenerating khoa moi nut sinh - 4c).

export type PanelMode = 'closed' | 'input' | 'collapsed'

// Loi cua mot phan: tu o FAILED (retryParts = [phan do]) hoac loi ca request (retryParts = cac phan cua request do khi
// 429/loi mang; null khi 400/404 - chi hien cau backend, 4i).
export interface PartError {
  message: string
  retryParts: JobDraftPart[] | null
  companyMissing: boolean
}

export interface PartState {
  running: boolean
  error: PartError | null
  // Da tung co ket qua thanh cong - loi khi tao lai thi giu goi y cu va bao "dang hien goi y truoc do" (4f).
  hadSuccess: boolean
}

export interface JobDraftResult {
  data: JobDraft
  token: string
}

export interface PendingCriterion {
  id: string
  name: string
  description: string
  token: string
}

// Tieu chi da giu - cua HR. weight la chuoi o nhap (trong ban dau - R-W1). aiName/aiDescription: gia tri AI goi y, de an
// dau "Do AI goi y" khi bi sua.
export interface KeptCriterion {
  id: string
  name: string
  description: string
  weight: string
  aiName: string
  aiDescription: string
}

export interface PendingQuestion {
  id: string
  questionType: ScreeningQuestionType
  questionText: string
  options: string[]
  token: string
}

export interface KeptQuestion {
  id: string
  questionType: ScreeningQuestionType
  questionText: string
  options: string[]
  // KHONG tick san (R-Q1).
  required: boolean
  aiEdited: boolean
}

const IDLE: PartState = { running: false, error: null, hadSuccess: false }

function sameText(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase()
}

// Loi ca request (4i): 429 va loi mang/qua 35 s co "Thu lai" gui lai dung cac phan; 400/404 chi hien cau backend.
function requestError(error: unknown, parts: JobDraftPart[]): PartError {
  if (isAxiosError(error) && error.response) {
    const status = error.response.status
    const data = error.response.data as { error?: unknown } | undefined
    if (status === 429) {
      return { message: extractErrorMessage(error, JOB_DRAFT_TEXT.generateFallbackError), retryParts: parts, companyMissing: false }
    }
    if (status >= 400 && status < 500) {
      return {
        message: extractErrorMessage(error, JOB_DRAFT_TEXT.generateFallbackError),
        retryParts: null,
        companyMissing: data?.error === 'COMPANY_NOT_FOUND',
      }
    }
  }
  return { message: JOB_DRAFT_TEXT.generateFallbackError, retryParts: parts, companyMissing: false }
}

export function useJobAiDraft() {
  const generateMutation = useGenerateJobDraftMutation()
  const idCounter = useRef(0)
  const nextId = useCallback((prefix: string) => {
    idCounter.current += 1
    return `${prefix}-${idCounter.current}`
  }, [])

  const [panelMode, setPanelMode] = useState<PanelMode>('closed')
  const [description, setDescription] = useState('')
  const [aiMode, setAiMode] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)
  const [parts, setParts] = useState<Record<JobDraftPart, PartState>>({ JOB: IDLE, CRITERIA: IDLE, QUESTIONS: IDLE })

  // Phan JOB: ket qua thanh cong GAN NHAT; filled = da bam "Dien vao bieu mau" cho ket qua nay.
  const [jobResult, setJobResult] = useState<JobDraftResult | null>(null)
  const [jobFilled, setJobFilled] = useState(false)
  // Gia tri AI da dien theo truong (dau "Do AI goi y", hop thoai ghi de 4f).
  const [aiFilled, setAiFilled] = useState<Partial<Record<FillableField, string>>>({})

  // So goi y cua lan sinh thanh cong gan nhat - 0 thi hien cau rong (4e/4g), khac voi "HR da xu ly het goi y".
  const [suggestedCounts, setSuggestedCounts] = useState({ CRITERIA: 0, QUESTIONS: 0 })
  const [pendingCriteria, setPendingCriteria] = useState<PendingCriterion[]>([])
  const [keptCriteria, setKeptCriteria] = useState<KeptCriterion[]>([])
  const [pendingQuestions, setPendingQuestions] = useState<PendingQuestion[]>([])
  const [keptQuestions, setKeptQuestions] = useState<KeptQuestion[]>([])

  // R-K4-5 - token cua lan sinh GAN NHAT ma HR da dung cho tung phan (dien / giu). Gui nguyen van o J2.
  const [usedTokens, setUsedTokens] = useState<Record<JobDraftPart, string | null>>({
    JOB: null,
    CRITERIA: null,
    QUESTIONS: null,
  })

  const setPart = (part: JobDraftPart, update: (state: PartState) => PartState) =>
    setParts((current) => ({ ...current, [part]: update(current[part]) }))

  function applyCell<T>(part: JobDraftPart, cell: PartResult<T> | null, onSuccess: (data: T, token: string) => void) {
    if (cell == null) {
      return
    }
    if (cell.status === 'SUCCEEDED' && cell.data != null && cell.generationToken != null) {
      onSuccess(cell.data, cell.generationToken)
      setPart(part, () => ({ running: false, error: null, hadSuccess: true }))
    } else {
      setPart(part, (state) => ({
        ...state,
        running: false,
        error: {
          message: cell.message ?? JOB_DRAFT_TEXT.generateFallbackError,
          retryParts: [part],
          companyMissing: false,
        },
      }))
    }
  }

  function applyResponse(response: JobAiDraftResponse) {
    applyCell<JobDraft>('JOB', response.job, (data, token) => {
      // Ban nhap moi KHONG tu dien - van phai bam "Dien vao bieu mau" (4f).
      setJobResult({ data, token })
      setJobFilled(false)
    })
    // Thay toan bo goi y CHUA CHON; muc da giu giu nguyen (4f). Goi y trung muc da giu bi an luc render (visible...).
    applyCell<CriteriaDraft>('CRITERIA', response.criteria, (data, token) => {
      setSuggestedCounts((current) => ({ ...current, CRITERIA: data.criteria.length }))
      setPendingCriteria(
        data.criteria.map((c) => ({ id: nextId('criterion'), name: c.name, description: c.description ?? '', token })),
      )
    })
    applyCell<QuestionsDraft>('QUESTIONS', response.questions, (data, token) => {
      setSuggestedCounts((current) => ({ ...current, QUESTIONS: data.questions.length }))
      setPendingQuestions(
        data.questions.map((q) => ({
          id: nextId('question'),
          questionType: q.questionType,
          questionText: q.questionText,
          options: q.options ?? [],
          token,
        })),
      )
    })
  }

  // MOT request J1 cho cac phan duoc chon (L9). Khong gui khi request truoc chua xong.
  async function generate(requested: JobDraftPart[]) {
    if (isGenerating || requested.length === 0 || description.trim().length === 0) {
      return
    }
    setIsGenerating(true)
    setAiMode(true)
    setParts((current) => {
      const next = { ...current }
      for (const part of requested) {
        next[part] = { ...current[part], running: true, error: null }
      }
      return next
    })
    try {
      const normalized = description.replace(/\r\n?/g, '\n')
      const response = await generateMutation.mutateAsync({ description: normalized, parts: requested })
      applyResponse(response)
      // 4c-2 - co response thi thu gon khoi nhap mo ta; loi ca request giu nguyen de HR sua mo ta.
      setPanelMode('collapsed')
    } catch (error) {
      const failure = requestError(error, requested)
      setParts((current) => {
        const next = { ...current }
        for (const part of requested) {
          next[part] = { ...current[part], running: false, error: failure }
        }
        return next
      })
    } finally {
      setIsGenerating(false)
    }
  }

  // ---- phan JOB ----

  function markJobFilled(filled: Partial<Record<FillableField, string>>) {
    if (jobResult == null) {
      return
    }
    setAiFilled((current) => ({ ...current, ...filled }))
    setJobFilled(true)
    setUsedTokens((current) => ({ ...current, JOB: jobResult.token }))
  }

  // ---- tieu chi (4e) ----

  // Goi y trung ten (khong phan biet hoa thuong) voi tieu chi da giu thi khong hien (4f).
  const visiblePendingCriteria = pendingCriteria.filter((c) => !keptCriteria.some((k) => sameText(k.name, c.name)))
  const visiblePendingQuestions = pendingQuestions.filter(
    (q) => !keptQuestions.some((k) => sameText(k.questionText, q.questionText)),
  )

  function keepCriteria(ids: string[]): string[] {
    const room = MAX_KEPT_CRITERIA - keptCriteria.length
    const chosen = visiblePendingCriteria.filter((c) => ids.includes(c.id)).slice(0, Math.max(room, 0))
    if (chosen.length === 0) {
      return []
    }
    const newKept = chosen.map<KeptCriterion>((c) => ({
      id: c.id,
      name: c.name,
      description: c.description,
      weight: '',
      aiName: c.name,
      aiDescription: c.description,
    }))
    setKeptCriteria((current) => [...current, ...newKept])
    setPendingCriteria((current) => current.filter((c) => !chosen.some((k) => k.id === c.id)))
    setUsedTokens((current) => ({ ...current, CRITERIA: chosen[chosen.length - 1].token }))
    return newKept.map((c) => c.id)
  }

  function discardPendingCriterion(id: string) {
    setPendingCriteria((current) => current.filter((c) => c.id !== id))
  }

  function updateKeptCriterion(id: string, update: Partial<Pick<KeptCriterion, 'name' | 'description' | 'weight'>>) {
    setKeptCriteria((current) => current.map((c) => (c.id === id ? { ...c, ...update } : c)))
  }

  function removeKeptCriterion(id: string) {
    setKeptCriteria((current) => current.filter((c) => c.id !== id))
  }

  // ---- cau hoi (4g) ----

  function keepQuestions(ids: string[]): string[] {
    const room = MAX_KEPT_QUESTIONS - keptQuestions.length
    const chosen = visiblePendingQuestions.filter((q) => ids.includes(q.id)).slice(0, Math.max(room, 0))
    if (chosen.length === 0) {
      return []
    }
    const newKept = chosen.map<KeptQuestion>((q) => ({
      id: q.id,
      questionType: q.questionType,
      questionText: q.questionText,
      options: q.options,
      required: false,
      aiEdited: false,
    }))
    setKeptQuestions((current) => [...current, ...newKept])
    setPendingQuestions((current) => current.filter((q) => !chosen.some((k) => k.id === q.id)))
    setUsedTokens((current) => ({ ...current, QUESTIONS: chosen[chosen.length - 1].token }))
    return newKept.map((q) => q.id)
  }

  function discardPendingQuestion(id: string) {
    setPendingQuestions((current) => current.filter((q) => q.id !== id))
  }

  function updateKeptQuestion(id: string, update: Partial<Omit<KeptQuestion, 'id'>>) {
    setKeptQuestions((current) => current.map((q) => (q.id === id ? { ...q, ...update } : q)))
  }

  function removeKeptQuestion(id: string) {
    setKeptQuestions((current) => current.filter((q) => q.id !== id))
  }

  // R-K4-5 - chi gui token cua phan HR da dung va con muc cua phan do trong ban luu.
  function tokensForSave(): Record<JobDraftPart, string | null> {
    return {
      JOB: usedTokens.JOB,
      CRITERIA: keptCriteria.length > 0 ? usedTokens.CRITERIA : null,
      QUESTIONS: keptQuestions.length > 0 ? usedTokens.QUESTIONS : null,
    }
  }

  return {
    panelMode,
    setPanelMode,
    description,
    setDescription,
    aiMode,
    isGenerating,
    parts,
    generate,
    jobResult,
    jobFilled,
    aiFilled,
    markJobFilled,
    suggestedCounts,
    pendingCriteria: visiblePendingCriteria,
    keptCriteria,
    keepCriteria,
    discardPendingCriterion,
    updateKeptCriterion,
    removeKeptCriterion,
    pendingQuestions: visiblePendingQuestions,
    keptQuestions,
    keepQuestions,
    discardPendingQuestion,
    updateKeptQuestion,
    removeKeptQuestion,
    tokensForSave,
  }
}

export type JobAiDraftState = ReturnType<typeof useJobAiDraft>
