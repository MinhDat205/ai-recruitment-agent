import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { extractErrorMessage } from '../../lib/httpError'
import { askCandidateCvQuestion, askHrCvQuestion } from './api'
import { CV_QA_TEXT } from './cvQaLabels'
import type { CvQaSide, CvQaTurn } from './types'

// State cua mot phien hoi dap, GAN voi khoa (R-N3): key = applicationId (HR) hoac resumeId (ung vien).
interface CvQaSessionState {
  key: string
  // The he phien: tang moi lan khoa doi. Ket qua/loi chi duoc ghi khi epoch luc gui con trung (L50).
  epoch: number
  // Luot moi nhat o dau mang (O7).
  turns: CvQaTurn[]
  // Cau loi dang hien (4b-4), null khi khong co loi.
  error: string | null
  // Cau hoi cua lan loi - "Thu lai" gui dung cau nay, khong doc lai o hoi (UI.md muc 6).
  retryQuestion: string | null
  // Dang cho request cua CHINH khoa nay - khong lay mutation.isPending (request cua khoa cu con treo khong duoc khoa
  // nut cua khoa moi).
  pending: boolean
}

export interface CvQaSession {
  key: string
  turns: CvQaTurn[]
  error: string | null
  retryQuestion: string | null
  pending: boolean
  // Goi khi cau hoi da qua kiem phia client (CvQaPanel).
  ask: (question: string) => void
  retry: () => void
}

interface AskVariables {
  question: string
  sentKey: string
}

function emptyState(key: string, epoch: number): CvQaSessionState {
  return { key, epoch, turns: [], error: null, retryQuestion: null, pending: false }
}

// id cua luot chi dung lam key khi render; sinh trong callback, khong trong luc render.
let turnSeq = 0
function nextTurnId(): string {
  turnSeq += 1
  return `cvqa-turn-${turnSeq}`
}

// FR-C08 - phien hoi dap CV, goi o CAP TRANG (khong trong component tab) de doi tab khong mat luot (a25). Luot chi
// nam trong state React - khong luu vao bo nho trinh duyet, khong gui luot truoc len backend (R-N1).
export function useCvQaSession(side: CvQaSide, key: string): CvQaSession {
  const [state, setState] = useState<CvQaSessionState>(() => emptyState(key, 0))

  // Khong tu thu lai: moi lan goi ton mot luot nhom ai-sync va mot loi goi AI; nguoi dung bam "Thu lai".
  const mutation = useMutation({
    mutationFn: ({ question, sentKey }: AskVariables) =>
      side === 'hr' ? askHrCvQuestion(sentKey, question) : askCandidateCvQuestion(sentKey, question),
    retry: false,
  })

  // R-N3 (1) - khoa doi: xoa sach ngay trong lan render nay (mau "dieu chinh state khi prop doi" cua React), khong cho
  // useEffect - khong co khung hinh nao hien luot/loi cua khoa cu. Moi lan doi khoa la mot the he phien moi.
  let current = state
  if (state.key !== key) {
    current = emptyState(key, state.epoch + 1)
    setState(current)
  }

  async function send(question: string) {
    // R-N3 (2) - ghi the he phien luc gui; ket qua/loi ve ma the he da khac thi giu nguyen prev (bo ket qua).
    // So theo khoa KHONG du (L50): A -> B -> A voi request A cu con treo thi khoa lai trung, luot cu lot vao phien
    // moi va pending bi tat som trong khi request moi con dang cho. Epoch tang o moi lan doi khoa nen khong trung lai.
    const sentKey = key
    const sentEpoch = current.epoch
    setState((prev) =>
      prev.epoch === sentEpoch ? { ...prev, pending: true, error: null, retryQuestion: null } : prev,
    )
    try {
      const response = await mutation.mutateAsync({ question, sentKey })
      // R-N3 (3) - thanh cong cung the he: them luot vao DAU mang.
      setState((prev) =>
        prev.epoch === sentEpoch
          ? { ...prev, pending: false, turns: [{ id: nextTurnId(), question, response }, ...prev.turns] }
          : prev,
      )
    } catch (err) {
      // Cau message cua backend neu co {error, message}; khong co (mang, timeout, 5xx khac) -> cau du phong.
      const message = extractErrorMessage(err, CV_QA_TEXT.askFailedFallback)
      setState((prev) =>
        prev.epoch === sentEpoch ? { ...prev, pending: false, error: message, retryQuestion: question } : prev,
      )
    }
  }

  function ask(question: string) {
    if (current.pending) {
      return
    }
    void send(question)
  }

  function retry() {
    if (current.pending || current.retryQuestion === null) {
      return
    }
    void send(current.retryQuestion)
  }

  return {
    key: current.key,
    turns: current.turns,
    error: current.error,
    retryQuestion: current.retryQuestion,
    pending: current.pending,
    ask,
    retry,
  }
}
