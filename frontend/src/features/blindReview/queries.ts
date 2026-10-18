import { useEffect, useRef } from 'react'
import { hashKey, useMutation, useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query'
import { APPLICATION_DETAIL_KEY_PREFIX, applicationDetailKey } from '../applicationDetail/queries'
import { SCORING_RUN_AUDIT_KEY_PREFIX, candidatesKeyPrefix } from '../candidates/queries'
import { applicationComparisonKeyPrefix } from '../comparison/queries'
import { INTERVIEW_INVITATION_PREVIEW_KEY_PREFIX } from '../interviewinvitation/queries'
import { HR_JOBS_KEY, hrJobDetailKey } from '../jobs/ownerQueries'
import { MESSAGE_CONVERSATIONS_KEY_PREFIX, MESSAGE_THREAD_KEY_PREFIX } from '../messages/queries'
import { hrApplicationsKeyPrefix } from '../scoring/queries'
import { APPLICATION_SCREENING_KEY_PREFIX } from '../screening/queries'
import { TALENT_POOLS_KEY } from '../talentPools/queries'
import { updateBlindReviewRequest } from './api'

// FR-H16 L11 - MOI query phia HR co du lieu doi theo trang thai an danh (ten, noi dung da che, ten tep). B1 doi trang
// thai cua moi don trong Job nhung frontend khong biet id cac don do -> lam moi theo tien to; query chua mount chi bi
// danh dau stale. Khong them rieng: luot cham, kich ban soan nhap, P6, giay moi I1, thong bao (khong chua danh tinh,
// hoac luu nguyen luc phat sinh); lich su E5 bi lam moi kem vi chung tien to. Khoa Job KHONG nam o day (jobKeys).
function identityDependentKeys(jobId: string): QueryKey[] {
  return [
    hrApplicationsKeyPrefix(jobId),
    // E1-E5 va CV da trich xuat cua MOI don.
    [APPLICATION_DETAIL_KEY_PREFIX],
    [APPLICATION_SCREENING_KEY_PREFIX],
    candidatesKeyPrefix(),
    [SCORING_RUN_AUDIT_KEY_PREFIX],
    applicationComparisonKeyPrefix(jobId),
    TALENT_POOLS_KEY,
    [MESSAGE_THREAD_KEY_PREFIX, 'hr'],
    [MESSAGE_CONVERSATIONS_KEY_PREFIX],
    [INTERVIEW_INVITATION_PREVIEW_KEY_PREFIX],
  ]
}

// Job chi mang blindReviewEnabled, khong co danh tinh. Tach rieng: reset khoa nay se dua trang sua tin dang mo ve nhanh
// isLoading ("Dang tai...") va go form + card an danh.
function jobKeys(jobId: string): QueryKey[] {
  return [hrJobDetailKey(jobId), HR_JOBS_KEY]
}

// skipKey: query vua tai xong (E1 o trang ho so don), khong tai lai lan nua.
function invalidateIdentityDependentQueries(queryClient: QueryClient, jobId: string, skipKey?: QueryKey) {
  const skipHash = skipKey ? hashKey(skipKey) : null
  return Promise.all(
    [...jobKeys(jobId), ...identityDependentKeys(jobId)].map((queryKey) =>
      queryClient.invalidateQueries({ queryKey, predicate: (query) => query.queryHash !== skipHash }),
    ),
  )
}

// FR-H16 B1 (UI.md 4a, 5a) - thanh cong: lam moi Job (blindReviewEnabled) va RESET moi query co du lieu phu thuoc an danh
// (L11): xoa cache de khong bao gio hien du lieu cua trang thai truoc, chap nhan skeleton mot nhip; query dang mount tai
// lai ngay. Dung lai khoa co san, khong khai lai.
export function useUpdateBlindReviewMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (enabled: boolean) => updateBlindReviewRequest(jobId, enabled),
    onSuccess: () =>
      Promise.all([
        ...jobKeys(jobId).map((queryKey) => queryClient.invalidateQueries({ queryKey })),
        ...identityDependentKeys(jobId).map((queryKey) => queryClient.resetQueries({ queryKey })),
      ]),
  })
}

interface MaskedDetail {
  id: string
  jobId: string
  identityMasked: boolean
}

// FR-H16 L11 - trang ho so don: E1 tra identityMasked khac lan truoc (cung don) -> cac query khac co the con du lieu cua
// trang thai cu (vd HR doi o tab trinh duyet khac): lam moi lai (tru E1 vua tai) va goi onChange (bo phien hoi dap CV).
// Lan dau thay don thi chi ghi nho, khong lam moi.
export function useIdentityMaskChangeSync(detail: MaskedDetail | undefined, onChange: () => void) {
  const queryClient = useQueryClient()
  const previous = useRef<{ applicationId: string; masked: boolean } | null>(null)
  const applicationId = detail?.id
  const jobId = detail?.jobId
  const masked = detail?.identityMasked

  useEffect(() => {
    if (applicationId === undefined || jobId === undefined || masked === undefined) {
      return
    }
    const prev = previous.current
    previous.current = { applicationId, masked }
    if (prev !== null && prev.applicationId === applicationId && prev.masked !== masked) {
      void invalidateIdentityDependentQueries(queryClient, jobId, applicationDetailKey(applicationId))
      onChange()
    }
  }, [applicationId, jobId, masked, queryClient, onChange])
}
