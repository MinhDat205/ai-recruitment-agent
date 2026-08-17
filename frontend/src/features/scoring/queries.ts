import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient, type Query } from '@tanstack/react-query'
import { createScoringRunRequest, listHrApplicationsRequest, listScoringRunsRequest } from './api'
import type { ApplicationHrListItem, ScoringRun } from './types'

function hrApplicationsKey(jobId: string) {
  return ['hr-applications', jobId]
}

function scoringRunsKey(applicationId: string) {
  return ['scoring-runs', applicationId]
}

// Poller backend chay moi 5s (app.scoring.poll-interval-ms) - refetch phia FE quanh chu ky do,
// giong tinh than RESUME_LIST_POLL_INTERVAL_MS (features/resumes/queries.ts).
const POLL_INTERVAL_MS = 5000

// Moc CUNG de TU DUNG poll va doi HR chu dong bam "Tai lai", ap dung cho CA vong ngoai (danh sach
// don) LAN vong trong (chi tiet mot lot cham). Ly do can co moc cung: mot luot cham RUNNING/
// finished_at NULL co the ket VINH VIEN khi JVM backend chet giua chung (gioi han da biet cua D2,
// xem Q1(iii) trong ke hoach D2 - khong co duong nao o tang backend phan biet duoc "dang cham
// that" voi "da chet", nen frontend cung khong the tu suy ra, chi co the gioi han thoi gian cho).
//
// 10 phut - gap doi RESUME_STALLED_THRESHOLD_MS cua D1 (5 phut, cho MOT lan goi LLM duy nhat cho
// ca CV). Mot luot cham o day co the goi LLM TUAN TU N lan (N = so tieu chi cua rubric, moi lan
// toi ~60 giay gom ca 1 lan retry khi JSON hong - xem CriterionScoringService). 10 phut = du cho
// rubric co toi ~8-10 tieu chi trong tinh huong cham nhat (10 x 60s = 600s), van du ngan de khong
// bat HR cho vo nghia neu ban ghi that su ket vinh vien.
const MAX_POLL_DURATION_MS = 10 * 60 * 1000

function hasApplicationWithRunInProgress(applications: ApplicationHrListItem[] | undefined): boolean {
  return (applications ?? []).some(
    (app) =>
      app.latestScoringRunFinishedAt === null &&
      (app.latestScoringRunStatus === 'PENDING' || app.latestScoringRunStatus === 'RUNNING'),
  )
}

function isLatestRunInProgress(runs: ScoringRun[] | undefined): boolean {
  const latest = runs?.[0]
  return Boolean(latest && latest.finishedAt === null)
}

// Dung chung cho CA hai vong poll (Dot 5, yeu cau bo sung): dem thoi gian ke tu luc `inProgress`
// chuyen tu false sang true, tu dong bao timedOut=true khi qua `timeoutMs`. Dung setTimeout trong
// useEffect (KHONG setState truc tiep trong refetchInterval cua react-query) de tranh cap nhat
// state ngoai chu ky render an toan. resumeEpoch tang moi lan HR bam "Tai lai" de TAI VU (re-arm)
// bo dem tu dau - HR bam duoc nhieu lan, khong chi mot lan duy nhat neu ban ghi van tiep tuc ket.
function useStallGuardedRefetch(inProgress: boolean, timeoutMs: number) {
  const [timedOut, setTimedOut] = useState(false)
  const [resumeEpoch, setResumeEpoch] = useState(0)

  useEffect(() => {
    if (!inProgress) {
      return
    }
    // setTimedOut(true) o day la goi TRONG CALLBACK BAT DONG BO cua setTimeout - mau duoc
    // eslint-plugin-react-hooks chap nhan ("calling setState in a callback function when external
    // state changes"). setTimedOut(false) o cleanup (khong phai o than effect) cho dung ly do
    // tuong tu - chay khi inProgress chuyen ve false (lot cham xong truoc khi het gio) HOAC khi
    // resumeEpoch doi (HR bam "Tai lai", can huy dong ho cu truoc khi dat dong ho moi).
    const timer = setTimeout(() => setTimedOut(true), timeoutMs)
    return () => {
      clearTimeout(timer)
      setTimedOut(false)
    }
  }, [inProgress, timeoutMs, resumeEpoch])

  function resumePolling() {
    setTimedOut(false)
    setResumeEpoch((epoch) => epoch + 1)
  }

  return { timedOut, resumePolling }
}

// Vong NGOAI: danh sach don cua ca job. `inProgress` cho bo dem stall duoc doc tu CACHE hien co
// (queryClient.getQueryData) THAY VI tu ket qua useQuery ben duoi cung ham - tranh tham chieu
// nguoc "timedOut" (can co truoc) vao chinh useQuery dang dinh nghia no. Cache da co du lieu ngay
// tu lan fetch dau (hoac undefined truoc do, luc ay hasApplicationWithRunInProgress tra false, hop
// ly - chua biet gi thi coi nhu khong co gi dang chay).
export function useHrApplicationsQuery(jobId: string) {
  const queryClient = useQueryClient()
  const cachedData = queryClient.getQueryData<ApplicationHrListItem[]>(hrApplicationsKey(jobId))
  const { timedOut, resumePolling: resetStallTimer } = useStallGuardedRefetch(
    hasApplicationWithRunInProgress(cachedData),
    MAX_POLL_DURATION_MS,
  )

  const query = useQuery({
    queryKey: hrApplicationsKey(jobId),
    queryFn: () => listHrApplicationsRequest(jobId),
    refetchInterval: (q: Query<ApplicationHrListItem[]>) =>
      !timedOut && hasApplicationWithRunInProgress(q.state.data) ? POLL_INTERVAL_MS : false,
  })

  return {
    ...query,
    timedOut,
    resumePolling: () => {
      resetStallTimer()
      query.refetch()
    },
  }
}

// Vong TRONG: danh sach lot cham cua MOT don, moi nhat truoc (server da sap) - dung o tang row de
// doc criteriaScored/criteriaTotal (khong co san tren ApplicationHrListItemResponse, chi
// ScoringRunResponse moi co) va errorMessage khi FAILED. finishedAt cua lot MOI NHAT la tin hieu
// DUY NHAT de dung polling BINH THUONG (khong tu dem N/N o client) - dung yeu cau Dot 5; timedOut
// la lop chan THEM cho truong hop finishedAt khong bao gio den (xem MAX_POLL_DURATION_MS).
export function useScoringRunsQuery(applicationId: string, enabled: boolean) {
  const queryClient = useQueryClient()
  const cachedData = queryClient.getQueryData<ScoringRun[]>(scoringRunsKey(applicationId))
  const { timedOut, resumePolling: resetStallTimer } = useStallGuardedRefetch(
    isLatestRunInProgress(cachedData),
    MAX_POLL_DURATION_MS,
  )

  const query = useQuery({
    queryKey: scoringRunsKey(applicationId),
    queryFn: () => listScoringRunsRequest(applicationId),
    enabled,
    refetchInterval: (q: Query<ScoringRun[]>) =>
      !timedOut && isLatestRunInProgress(q.state.data) ? POLL_INTERVAL_MS : false,
  })

  return {
    ...query,
    timedOut,
    resumePolling: () => {
      resetStallTimer()
      query.refetch()
    },
  }
}

export function useCreateScoringRunMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (applicationId: string) => createScoringRunRequest(applicationId),
    onSuccess: (_data, applicationId) => {
      queryClient.invalidateQueries({ queryKey: hrApplicationsKey(jobId) })
      queryClient.invalidateQueries({ queryKey: scoringRunsKey(applicationId) })
    },
  })
}
