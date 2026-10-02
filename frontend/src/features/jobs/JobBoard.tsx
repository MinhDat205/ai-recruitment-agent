import { JobFilterBar } from './JobFilterBar'
import { JobList } from './JobList'
import { useJobsQuery } from './queries'
import { useJobFilters } from './useJobFilters'

// Noi dung "Viec lam" dung chung cho "/" (khach/ung vien chua dang nhap) va "/candidate" (ung vien
// da dang nhap) - R-L3: hai trang chi con khac o layout boc ngoai (PublicLayout/CandidateLayout),
// toan bo thanh loc + so ket qua + danh sach + phan trang nam trong 1 component nay. useJobsQuery
// goi DUNG MOT lan o day (truoc day moi trang goi rieng + JobList tu goi lai = 2 lan cho cung 1
// apiParams) - JobList gio chi nhan data/trang thai qua props, khong tu fetch.
export function JobBoard() {
  const { filters, apiParams, setPage, canQueryJobs, hasActiveFilters, clearFilters } = useJobFilters()
  const jobsQuery = useJobsQuery(apiParams, { enabled: canQueryJobs })

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
      <JobFilterBar totalElements={jobsQuery.data?.totalElements} />
      <h2 className="mt-6 mb-4 text-xl font-semibold text-ink">Việc làm đang tuyển</h2>
      <JobList
        data={jobsQuery.data}
        isPending={jobsQuery.isPending}
        isError={jobsQuery.isError}
        onRetry={() => jobsQuery.refetch()}
        onPageChange={setPage}
        hasActiveFilters={hasActiveFilters}
        onClearFilters={clearFilters}
        filterContext={{
          isCategoryFilterActive: filters.categoryCode != null,
          isLocationFilterActive: filters.locationCode != null,
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
