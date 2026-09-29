import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { HrLayout } from '../../components/layout/HrLayout'
import { isCompanyNotCreatedError, useMyCompanyQuery } from './ownerQueries'

// Chan cac trang HR phu thuoc cong ty (dashboard, tin tuyen dung, ung vien...) khi HR chua tao
// ho so cong ty: chuyen toi /hr/company thay vi de trang con goi API roi hien loi 404.
// Dung lai dung query "cong ty cua toi" (cung queryKey voi CompanyProfilePage) nen khong goi API
// trung. Chi chuyen huong khi CHAC CHAN la "chua co cong ty"; loi khac (mang, 500, 401) van hien
// trang con de luong xu ly loi san co cua tung trang lo.
export function RequireCompany({ children }: { children: ReactNode }) {
  const { isLoading, isError, error } = useMyCompanyQuery()

  if (isLoading) {
    return (
      <HrLayout title="Đang tải">
        <div className="flex flex-col gap-4" aria-busy="true">
          <div className="h-8 w-64 animate-pulse rounded-md bg-line" />
          <div className="h-32 animate-pulse rounded-md bg-line" />
          <div className="h-32 animate-pulse rounded-md bg-line" />
        </div>
      </HrLayout>
    )
  }

  if (isError && isCompanyNotCreatedError(error)) {
    return <Navigate to="/hr/company" replace state={{ needCompany: true }} />
  }

  return children
}
