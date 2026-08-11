import { HrLayout } from '../components/layout/HrLayout'
import { useAuth } from '../features/auth/useAuth'

export function HrHomePage() {
  const { user } = useAuth()

  return (
    <HrLayout title="Dashboard">
      <h1 className="text-2xl font-semibold text-ink">Xin chào {user?.fullName}</h1>
    </HrLayout>
  )
}
