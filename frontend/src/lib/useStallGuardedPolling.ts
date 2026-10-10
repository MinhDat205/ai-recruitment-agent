import { useEffect, useState } from 'react'

// FR-C06 R-C4 - chuyen nguyen tu features/notifications/queries.ts de dung chung (chuong thong bao, tab
// "Trao doi", hop thu). Nguyen tac stall-guard cua du an: khong polling vo han - dung MOT nguong thoi gian lien
// tuc toi da, het nguong thi timedOut = true va noi goi tu dung refetchInterval; resumePolling() chay lai
// dong ho tu dau (nguoi dung bam "Tai lai" / mo lai chuong).
export function useStallGuardedPolling(timeoutMs: number) {
  const [timedOut, setTimedOut] = useState(false)
  const [resumeEpoch, setResumeEpoch] = useState(0)

  useEffect(() => {
    const timer = setTimeout(() => setTimedOut(true), timeoutMs)
    return () => {
      clearTimeout(timer)
      setTimedOut(false)
    }
  }, [timeoutMs, resumeEpoch])

  function resumePolling() {
    setTimedOut(false)
    setResumeEpoch((epoch) => epoch + 1)
  }

  return { timedOut, resumePolling }
}
