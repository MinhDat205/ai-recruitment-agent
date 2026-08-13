import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'

export function CandidateHomePage() {
  const { user, logout } = useAuth()

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold text-ink">Xin chào {user?.fullName}</h1>
      <div className="mt-4 flex gap-3">
        <Link
          to="/candidate/profile"
          className="flex h-10 items-center rounded-md bg-brand px-5 text-sm font-medium text-white"
        >
          Hồ sơ & CV của tôi
        </Link>
        <Link
          to="/candidate/applications"
          className="flex h-10 items-center rounded-md border border-brand px-5 text-sm font-medium text-brand"
        >
          Đơn ứng tuyển của tôi
        </Link>
        <button
          type="button"
          onClick={logout}
          className="h-10 rounded-md border border-brand px-5 text-sm font-medium text-brand"
        >
          Đăng xuất
        </button>
      </div>
    </div>
  )
}
