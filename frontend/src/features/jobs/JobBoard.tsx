import { JobFilterBar } from './JobFilterBar'
import { JobList } from './JobList'
import { RecommendedJobs } from './RecommendedJobs'
import { useJobsQuery } from './queries'
import { useJobFilters } from './useJobFilters'

interface JobBoardProps {
  // FR-U15 R-L3/R-L4 - true o /candidate (CandidateJobListPage truyen vao), false (mac dinh,
  // khong doi) o "/" (PublicJobListPage khong truyen) - trang cong khai/khach khong co khoi goi y.
  showRecommendations?: boolean
}

// Noi dung "Viec lam" dung chung cho "/" (khach/ung vien chua dang nhap) va "/candidate" (ung vien
// da dang nhap) - R-L3: hai trang chi con khac o layout boc ngoai (PublicLayout/CandidateLayout),
// toan bo thanh loc + so ket qua + danh sach + phan trang nam trong 1 component nay. useJobsQuery
// goi DUNG MOT lan o day (truoc day moi trang goi rieng + JobList tu goi lai = 2 lan cho cung 1
// apiParams) - JobList gio chi nhan data/trang thai qua props, khong tu fetch.
export function JobBoard({ showRecommendations = false }: JobBoardProps) {
  const { filters, apiParams, setPage, canQueryJobs, hasActiveFilters, clearFilters } = useJobFilters()
  const jobsQuery = useJobsQuery(apiParams, { enabled: canQueryJobs })

  // FR-U15 R-L3 - khoi goi y CHI hien khi: showRecommendations=true, dang o trang 1, chua co tu
  // khoa, khong co bo loc nao dang ap. Khi dieu kien nay sai, RecommendedJobs KHONG duoc render -
  // khong goi API goi y (tranh goi thua khong can thiet khi khong hien).
  const showRecommendedBlock =
    showRecommendations && filters.page === 0 && filters.keyword === '' && !hasActiveFilters

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
      {showRecommendedBlock && (
        <div className="mb-8">
          <RecommendedJobs />
        </div>
      )}
      <JobFilterBar totalElements={jobsQuery.data?.totalElements} />
      <h2 className="mt-6 mb-4 text-xl font-semibold text-m3-on-surface">Việc làm đang tuyển</h2>
      <JobList
        data={jobsQuery.data}
        isPending={jobsQuery.isPending}
        isError={jobsQuery.isError}
        onRetry={() => jobsQuery.refetch()}
        onPageChange={setPage}
        hasActiveFilters={hasActiveFilters}
        onClearFilters={clearFilters}
        filterContext={{
          // FR-U15 dot 6 doi categoryCode/locationCode sang string[] - sua lai dieu kien cho khop
          // (truoc la "!= null", luon dung voi kieu mang khong bao gio null, thanh loi am tham).
          isCategoryFilterActive: filters.categoryCode.length > 0,
          isLocationFilterActive: filters.locationCode.length > 0,
          // R-S5: chu thich ngoai te giai thich vi sao tin "lech" khoi khoang luong dang loc van
          // hien - chi co nghia khi THAT SU co khoang loc (salaryMin/salaryMax). hideUnlisted
          // dung mot minh (R-S4, khong kem salaryMin/salaryMax) khong so khoang nao ca nen khong
          // tinh vao day.
          isSalaryFilterActive: filters.salaryMin != null || filters.salaryMax != null,
        }}
      />
    </div>
  )
}
