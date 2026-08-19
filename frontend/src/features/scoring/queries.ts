import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient, type Query } from '@tanstack/react-query'
import type { ApplicationStatus } from '../applications/types'
import { changeApplicationStatusRequest, createScoringRunRequest, listHrApplicationsRequest, listScoringRunsRequest } from './api'
import type { ApplicationHrListItem, ApplicationSortOption, ScoringRun } from './types'

const HR_APPLICATIONS_KEY_PREFIX = 'hr-applications'

// Key DAY DU (dung cho queryFn/getQueryData) - sort la MOT PHAN cua key vi doi sort phai fetch lai
// (thu tu tra ve tu backend khac nhau, khong the tai su dung cache cua sort kia).
function hrApplicationsKey(jobId: string, sort: ApplicationSortOption) {
  return [HR_APPLICATIONS_KEY_PREFIX, jobId, sort]
}

// Key RUT GON (dung cho invalidateQueries) - CO Y KHONG co sort, de mot lan tao luot cham moi lam
// stale CA HAI bien the sort dang cache (HR co the doi qua lai giua hai kieu sort ma khong mat du
// lieu moi), khong chi bien the dang xem. Export (Dot 3, FR-H07) - features/interviewinvitation/
// cung can invalidate danh sach nay sau khi gui loi moi thanh cong (doi don sang INTERVIEW_INVITED).
export function hrApplicationsKeyPrefix(jobId: string) {
  return [HR_APPLICATIONS_KEY_PREFIX, jobId]
}

function scoringRunsKey(applicationId: string) {
  return ['scoring-runs', applicationId]
}

// Poller backend chay moi 5s (app.scoring.poll-interval-ms / app.aggregation.poll-interval-ms) -
// refetch phia FE quanh chu ky do, giong tinh than RESUME_LIST_POLL_INTERVAL_MS (features/resumes/queries.ts).
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

// Dieu kien "van con dang xu ly, phai tiep tuc poll" - MO RONG o Dot 5 (D3, FR-H05) de bao trum ca
// giai doan tong hop, KHONG CHI giai doan cham tung tieu chi cua D2 nhu truoc.
//
// Truoc D3: dieu kien la `finishedAt === null && status in {PENDING, RUNNING}` - dung vi luc do
// D2 la buoc CUOI CUNG, finishedAt khac null la xong het.
//
// Sau D3: mot luot RUNNING voi finishedAt DA KHAC NULL van CHUA xong that su - do la dung luc D2
// cham xong toan bo tieu chi nhung D3 chua kip tong hop (CLAUDE.md muc 2b, hang RUNNING+finished_at
// khac NULL). Neu van dung dieu kien cu, FE se DUNG poll dung luc nay - tong diem se KHONG BAO GIO
// tu hien ra, HR phai tu tai lai trang moi thay (day chinh la loi da phat hien va sua o Dot 5).
//
// Dung `totalScore === null` lam tin hieu THAY CHO viec kiem lai `finishedAt`: finishAggregation
// (backend) LUON set status=DONE VA total_score CUNG mot luc trong MOT UPDATE (xem
// ScoringRunRepository.finishAggregation) - nen "status con la RUNNING" VA "totalScore con null"
// LUON di doi voi nhau trong ca hai giai doan (D2 dang cham: totalScore chac chan null vi D2
// khong bao gio ghi cot do; D3 dang cho: totalScore van null cho toi khi DONE). Gop lai thanh MOT
// dieu kien duy nhat, khong can nhanh re rieng cho tung giai doan.
function hasApplicationWithRunInProgress(applications: ApplicationHrListItem[] | undefined): boolean {
  return (applications ?? []).some(
    (app) => app.latestScoringRunStatus === 'PENDING' || (app.latestScoringRunStatus === 'RUNNING' && app.totalScore === null),
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
export function useHrApplicationsQuery(jobId: string, sort: ApplicationSortOption) {
  const queryClient = useQueryClient()
  const cachedData = queryClient.getQueryData<ApplicationHrListItem[]>(hrApplicationsKey(jobId, sort))
  const { timedOut, resumePolling: resetStallTimer } = useStallGuardedRefetch(
    hasApplicationWithRunInProgress(cachedData),
    MAX_POLL_DURATION_MS,
  )

  const query = useQuery({
    queryKey: hrApplicationsKey(jobId, sort),
    queryFn: () => listHrApplicationsRequest(jobId, sort),
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
// DUNG DE dung polling cho VONG NAY (khong doi voi Dot 5): ScoringRunResponse KHONG co totalScore
// (D3 khong mo rong DTO nay, chi mo rong .../applications) - moi thu vong nay hien thi
// (criteriaScored/criteriaTotal/errorMessage) da BIET DAY DU ngay khi D2 xong, D3 tong hop xong hay
// chua khong lam thay doi gi o day. timedOut la lop chan THEM cho truong hop finishedAt khong bao
// gio den (xem MAX_POLL_DURATION_MS).
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
      queryClient.invalidateQueries({ queryKey: hrApplicationsKeyPrefix(jobId) })
      queryClient.invalidateQueries({ queryKey: scoringRunsKey(applicationId) })
    },
  })
}

// FR-H07 (E1, Dot 3) - Tu choi/Trung tuyen (REJECTED/HIRED). Mau y het useCreateScoringRunMutation:
// invalidate danh sach de badge trang thai + nut hanh dong cap nhat theo trang thai moi.
export function useChangeApplicationStatusMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, status }: { applicationId: string; status: ApplicationStatus }) =>
      changeApplicationStatusRequest(applicationId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: hrApplicationsKeyPrefix(jobId) })
    },
  })
}
