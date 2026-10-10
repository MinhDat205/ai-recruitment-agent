import type { ReactNode } from 'react'
import { Briefcase } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import { NotificationBell } from '../../features/notifications/NotificationBell'
import { MobileNavButton, MobileNavLink, MobileNavSheet } from './MobileNavSheet'

interface NavItem {
  label: string
  to: string
  isActive: (pathname: string) => boolean
}

// Moi muc tu quyet dinh isActive rieng - KHONG dung chung mot quy tac exact/tien to cho tat ca:
// "/candidate" (Viec lam, trang landing cua candidate - xem CandidateJobListPage.tsx) va
// "/candidate/dashboard" (Bang tin) deu la trang la (khong co route con) nen dung exact match;
// "Ho so va CV"/"Don ung tuyen" co route con (vd /candidate/profile/...) nen dung prefix match.
// "Viec lam" dat DAU hang - trang candidate thay ngay sau dang nhap (LoginForm.tsx,
// ProtectedRoute.tsx deu redirect role CANDIDATE toi "/candidate"). "Viec lam" sang ca o
// /jobs/:id - PublicJobDetailPage boc CandidateLayout khi nguoi xem la ung vien (FR-U07 R-L1b).
const NAV_ITEMS: NavItem[] = [
  {
    label: 'Việc làm',
    to: '/candidate',
    isActive: (path) => path === '/candidate' || path.startsWith('/jobs/'),
  },
  { label: 'Bảng tin', to: '/candidate/dashboard', isActive: (path) => path === '/candidate/dashboard' },
  {
    label: 'Hồ sơ và CV',
    to: '/candidate/profile',
    isActive: (path) => path.startsWith('/candidate/profile'),
  },
  {
    label: 'Đơn ứng tuyển',
    to: '/candidate/applications',
    isActive: (path) => path.startsWith('/candidate/applications'),
  },
  // FR-C06 - hop thu. Khong co so tin chua doc tren muc nay (REQUIREMENT muc 6).
  {
    label: 'Tin nhắn',
    to: '/candidate/messages',
    isActive: (path) => path.startsWith('/candidate/messages'),
  },
]

// Thong bao (E2/FR-C03) khong co muc nav rieng - chuong NotificationBell da la loi vao
// (NotificationDropdown.notificationsPagePath tro dung /candidate/notifications cho role
// CANDIDATE, ca o link "Xem tat ca" lan tung thong bao khong co item.link rieng).
// UI_GUIDE muc 2: thanh tab ngang khi >= md; < md dung nut menu mo sheet (qua 5 dich nen khong
// dung thanh dieu huong duoi day).
export function CandidateLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const location = useLocation()

  function navLinkClass(active: boolean): string {
    return `shrink-0 whitespace-nowrap border-b-2 py-5 text-sm font-medium ${
      active ? 'border-m3-primary text-m3-primary' : 'border-transparent text-m3-on-surface hover:text-m3-primary'
    }`
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 border-b border-m3-outline-variant bg-m3-surface">
        <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-4 md:px-6">
          <Link to="/candidate" className="flex items-center gap-2 text-lg font-semibold text-m3-on-surface">
            <Briefcase size={22} className="text-m3-primary" aria-hidden="true" />
            AI Recruitment Agent
          </Link>

          <nav className="hidden items-center gap-6 md:flex">
            {NAV_ITEMS.map((item) => {
              const active = item.isActive(location.pathname)
              return (
                <Link
                  key={item.label}
                  to={item.to}
                  aria-current={active ? 'page' : undefined}
                  className={navLinkClass(active)}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>

          <div className="flex items-center gap-3">
            <NotificationBell />
            <div className="hidden items-center gap-3 md:flex">
              {user?.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.fullName} className="h-8 w-8 rounded-full object-cover" />
              ) : (
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-m3-primary-container text-sm font-medium text-m3-on-primary-container">
                  {user?.fullName?.charAt(0).toUpperCase()}
                </div>
              )}
              <span className="text-sm text-m3-on-surface">{user?.fullName}</span>
              <button
                type="button"
                onClick={logout}
                className="h-8 rounded-md border border-m3-outline px-3 text-sm text-m3-on-surface-variant hover:text-m3-on-surface"
              >
                Đăng xuất
              </button>
            </div>

            <MobileNavSheet triggerClassName="md:hidden">
              {user?.fullName && (
                <p className="px-4 pb-2 text-sm font-medium text-m3-on-surface">{user.fullName}</p>
              )}
              {NAV_ITEMS.map((item) => (
                <MobileNavLink key={item.label} to={item.to} active={item.isActive(location.pathname)}>
                  {item.label}
                </MobileNavLink>
              ))}
              <div className="mt-2 border-t border-m3-outline-variant pt-2">
                <MobileNavButton onClick={logout}>Đăng xuất</MobileNavButton>
              </div>
            </MobileNavSheet>
          </div>
        </div>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  )
}
