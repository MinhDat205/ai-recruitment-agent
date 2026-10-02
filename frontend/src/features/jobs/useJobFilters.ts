import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useCatalogsQuery } from '../catalog/queries'
import type { JobSearchParams, JobSort, PostedWithin, WorkMode } from './types'

const WORK_MODE_VALUES: readonly WorkMode[] = ['ONSITE', 'HYBRID', 'REMOTE']
const POSTED_WITHIN_VALUES: readonly PostedWithin[] = ['LAST_24H', 'LAST_7D', 'LAST_30D']
const SORT_VALUES: readonly JobSort[] = ['NEWEST', 'SALARY_DESC']
const DEFAULT_SORT: JobSort = 'NEWEST'
const PAGE_SIZE = 10

export interface JobFiltersState {
  keyword: string
  categoryCode: string | null
  locationCode: string | null
  salaryMin: number | null
  salaryMax: number | null
  hideUnlisted: boolean
  workMode: WorkMode[]
  postedWithin: PostedWithin | null
  sort: JobSort
  page: number
}

// Khong gom "page" - doi trang la hanh dong rieng (setPage), khong di qua applyFilter (R-U3 chi
// reset trang khi doi FILTER/sort, khong reset khi nguoi dung tu bam trang).
export type JobFilterPatch = Partial<Omit<JobFiltersState, 'page'>>

function parseNonNegativeInt(raw: string | null): number | null {
  if (raw == null) return null
  const trimmed = raw.trim()
  if (!/^\d+$/.test(trimmed)) return null
  return Number(trimmed)
}

function parseEnum<T extends string>(raw: string | null, allowed: readonly T[]): T | null {
  if (raw == null) return null
  return (allowed as readonly string[]).includes(raw) ? (raw as T) : null
}

// R-U4: tham so URL khong hop le -> bo qua DUNG tham so do, khong chan trang. categoryCode/
// locationCode can doi chieu danh muc (bat dong bo) - khi danh muc CHUA tai xong (undefined) tam
// chap nhan nguyen gia tri (giong hanh vi hien co cua CatalogCombobox dang disabled luc dang tai),
// khi danh muc da tai xong ma khong co trong danh sach moi coi la la va bo qua.
function parseFilters(
  searchParams: URLSearchParams,
  industryCodes: Set<string> | undefined,
  provinceCodes: Set<string> | undefined,
): JobFiltersState {
  const categoryCodeRaw = searchParams.get('categoryCode')
  const categoryCode =
    categoryCodeRaw && (industryCodes === undefined || industryCodes.has(categoryCodeRaw)) ? categoryCodeRaw : null

  const locationCodeRaw = searchParams.get('locationCode')
  const locationCode =
    locationCodeRaw && (provinceCodes === undefined || provinceCodes.has(locationCodeRaw)) ? locationCodeRaw : null

  const salaryMinParsed = parseNonNegativeInt(searchParams.get('salaryMin'))
  const salaryMaxParsed = parseNonNegativeInt(searchParams.get('salaryMax'))
  // URL cu/go tay co the co salaryMin > salaryMax (khong trong danh sach vi pham R-U4 liet ke san,
  // nhung giu dung tinh than "URL hong khong lam vo trang") - khong biet ben nao sai nen bo qua ca
  // hai, con hon gui len API va nhan 400 ngay luc tai trang.
  const salaryRangeInvalid =
    salaryMinParsed != null && salaryMaxParsed != null && salaryMinParsed > salaryMaxParsed
  const salaryMin = salaryRangeInvalid ? null : salaryMinParsed
  const salaryMax = salaryRangeInvalid ? null : salaryMaxParsed

  const workMode = searchParams
    .getAll('workMode')
    .filter((value): value is WorkMode => (WORK_MODE_VALUES as readonly string[]).includes(value))

  const pageRaw = Number(searchParams.get('page') ?? '0')
  const page = Number.isFinite(pageRaw) && pageRaw >= 0 ? Math.trunc(pageRaw) : 0

  return {
    keyword: searchParams.get('keyword') ?? '',
    categoryCode,
    locationCode,
    salaryMin,
    salaryMax,
    hideUnlisted: searchParams.get('hideUnlisted') === 'true',
    workMode,
    postedWithin: parseEnum(searchParams.get('postedWithin'), POSTED_WITHIN_VALUES),
    sort: parseEnum(searchParams.get('sort'), SORT_VALUES) ?? DEFAULT_SORT,
    page,
  }
}

const FILTER_PARAM_KEYS = [
  'keyword',
  'categoryCode',
  'locationCode',
  'salaryMin',
  'salaryMax',
  'hideUnlisted',
  'workMode',
  'postedWithin',
  'sort',
  'page',
] as const

// FR-U07 R-U: URL (query string) la nguon su that DUY NHAT cua bo loc (R-U1) - khong giu ban sao
// state cuc bo nao lech khoi day. Component chi doc qua "filters", ghi qua applyFilter/clearFilters/
// setPage; useSearchParams cua react-router tu phat sinh re-render khi URL doi tu ben ngoai (nut
// back/forward, dan link) nen "filters" luon khop URL hien tai (R-U2).
export function useJobFilters() {
  const [searchParams, setSearchParams] = useSearchParams()
  const catalogsQuery = useCatalogsQuery()

  const industryCodes = useMemo(
    () => (catalogsQuery.data ? new Set(catalogsQuery.data.industries.map((item) => item.code)) : undefined),
    [catalogsQuery.data],
  )
  const provinceCodes = useMemo(
    () => (catalogsQuery.data ? new Set(catalogsQuery.data.provinces.map((item) => item.code)) : undefined),
    [catalogsQuery.data],
  )

  const filters = useMemo(
    () => parseFilters(searchParams, industryCodes, provinceCodes),
    [searchParams, industryCodes, provinceCodes],
  )

  // Khi URL co categoryCode/locationCode ma danh muc CON "pending" (chua co data, chua loi) -> chua
  // biet ma co hop le hay khong, hoan goi API viec lam (khong gui ma chua kiem len backend). Danh
  // muc tai XONG (co data): parseFilters da loai ma khong hop le o tren, mo lai truy van voi tham
  // so sach. Danh muc tai LOI (isError, het "pending"): KHONG hoan nua - gui nguyen ma len API de
  // backend tu quyet dinh, vi khong con gi de doi chieu va treo trang mai mai cho danh muc se
  // khong bao gio toi la te hon mot lan goi API co the bi 400.
  const hasUnresolvedCatalogCode = Boolean(searchParams.get('categoryCode') || searchParams.get('locationCode'))
  const canQueryJobs = !(hasUnresolvedCatalogCode && catalogsQuery.isPending)

  const apiParams = useMemo<JobSearchParams>(
    () => ({
      keyword: filters.keyword || undefined,
      categoryCode: filters.categoryCode ?? undefined,
      locationCode: filters.locationCode ?? undefined,
      salaryMin: filters.salaryMin ?? undefined,
      salaryMax: filters.salaryMax ?? undefined,
      hideUnlisted: filters.hideUnlisted || undefined,
      workMode: filters.workMode.length > 0 ? filters.workMode : undefined,
      postedWithin: filters.postedWithin ?? undefined,
      sort: filters.sort,
      page: filters.page,
      size: PAGE_SIZE,
    }),
    [filters],
  )

  // "Xoa bo loc" chi hien khi co >=1 dieu kien LOC dang ap dung, khong tinh sort/page (UI.md muc 4a).
  const hasActiveFilters =
    filters.keyword !== '' ||
    filters.categoryCode != null ||
    filters.locationCode != null ||
    filters.salaryMin != null ||
    filters.salaryMax != null ||
    filters.hideUnlisted ||
    filters.workMode.length > 0 ||
    filters.postedWithin != null

  // So dieu kien hien tren badge nut "Loc (n)" o mobile (UI.md muc 4b, 7) - CHI tinh cac nhom nam
  // trong bottom sheet (nganh/tinh/luong/hinh thuc/thoi gian dang); tu khoa nam NGOAI sheet (van
  // hien truc tiep trong o nhap) nen khong tinh vao day.
  const activeFilterCount = [
    filters.categoryCode != null,
    filters.locationCode != null,
    filters.salaryMin != null || filters.salaryMax != null || filters.hideUnlisted,
    filters.workMode.length > 0,
    filters.postedWithin != null,
  ].filter(Boolean).length

  // R-U6: mot lan ap dung = DUNG MOT lan goi setSearchParams (mot muc lich su), bat ke patch doi
  // bao nhieu truong cung luc (vd popover luong doi ca salaryMin+salaryMax+hideUnlisted). R-U3: doi
  // filter/sort luon xoa "page" khoi URL (ve trang 1).
  const applyFilter = useCallback(
    (patch: JobFilterPatch) => {
      const next = new URLSearchParams(searchParams)
      next.delete('page')

      function setOrDelete(key: string, value: string | null) {
        if (value == null || value === '') {
          next.delete(key)
        } else {
          next.set(key, value)
        }
      }

      if ('keyword' in patch) setOrDelete('keyword', patch.keyword?.trim() ?? null)
      if ('categoryCode' in patch) setOrDelete('categoryCode', patch.categoryCode ?? null)
      if ('locationCode' in patch) setOrDelete('locationCode', patch.locationCode ?? null)
      if ('salaryMin' in patch) setOrDelete('salaryMin', patch.salaryMin != null ? String(patch.salaryMin) : null)
      if ('salaryMax' in patch) setOrDelete('salaryMax', patch.salaryMax != null ? String(patch.salaryMax) : null)
      if ('hideUnlisted' in patch) setOrDelete('hideUnlisted', patch.hideUnlisted ? 'true' : null)
      if ('postedWithin' in patch) setOrDelete('postedWithin', patch.postedWithin ?? null)
      if ('sort' in patch) setOrDelete('sort', patch.sort && patch.sort !== DEFAULT_SORT ? patch.sort : null)
      if ('workMode' in patch) {
        next.delete('workMode')
        for (const mode of patch.workMode ?? []) {
          next.append('workMode', mode)
        }
      }

      setSearchParams(next)
    },
    [searchParams, setSearchParams],
  )

  // R-U5: xoa het tham so loc, giu trang 1 (xoa page khoi URL), sap xep ve mac dinh NEWEST (xoa
  // sort) - mot lan goi setSearchParams duy nhat.
  const clearFilters = useCallback(() => {
    const next = new URLSearchParams(searchParams)
    for (const key of FILTER_PARAM_KEYS) {
      next.delete(key)
    }
    setSearchParams(next)
  }, [searchParams, setSearchParams])

  const setPage = useCallback(
    (page: number) => {
      const next = new URLSearchParams(searchParams)
      next.set('page', String(page))
      setSearchParams(next)
    },
    [searchParams, setSearchParams],
  )

  return {
    filters,
    apiParams,
    catalogsQuery,
    applyFilter,
    clearFilters,
    setPage,
    hasActiveFilters,
    activeFilterCount,
    canQueryJobs,
  }
}
