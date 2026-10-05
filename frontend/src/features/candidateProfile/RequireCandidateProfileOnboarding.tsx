import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { CandidateLayout } from '../../components/layout/CandidateLayout'
import { useMyProfileQuery } from './queries'

// Chan nhom route /candidate/* (TRU chinh /candidate/onboarding) khi ung vien CHUA qua man
// onboarding (onboardingCompletedAt == null, FR-U14 R-O1/R-O2). Dung lai useMyProfileQuery (cung
// queryKey voi CandidateProfilePage/CandidateOnboardingPage) nen khong goi API trung.
//
// Dang tai: PHAI hien trang thai dang tai, KHONG render con/KHONG dieu huong - tranh render
// /candidate mot nhip roi moi nhay sang onboarding, va tranh doc onboardingCompletedAt khi du lieu
// chua chac moi nhat (R-O2). Loi tai (mang/500): khong dieu huong, de trang con tu xu ly loi (dung
// nguyen tac RequireCompany).
export function RequireCandidateProfileOnboarding({ children }: { children: ReactNode }) {
  const { data: profile, isLoading, isError } = useMyProfileQuery()

  if (isLoading) {
    return (
      <CandidateLayout>
        <div className="mx-auto flex max-w-[1200px] flex-col gap-4 px-4 py-8 md:px-6" aria-busy="true">
          <div className="h-8 w-64 animate-pulse rounded-md bg-m3-surface-container-highest" />
          <div className="h-32 animate-pulse rounded-md bg-m3-surface-container-highest" />
          <div className="h-32 animate-pulse rounded-md bg-m3-surface-container-highest" />
        </div>
      </CandidateLayout>
    )
  }

  if (!isError && profile?.onboardingCompletedAt === null) {
    return <Navigate to="/candidate/onboarding" replace />
  }

  return children
}
