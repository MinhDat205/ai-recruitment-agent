import { useAuth } from '../features/auth/useAuth'

export function CandidateHomePage() {
  const { user, logout } = useAuth()

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold text-ink">Xin chào {user?.fullName}</h1>
      <button
        type="button"
        onClick={logout}
        className="mt-4 h-10 rounded-md border border-brand px-5 text-sm font-medium text-brand"
      >
        Đăng xuất
      </button>
    </div>
  )
}
