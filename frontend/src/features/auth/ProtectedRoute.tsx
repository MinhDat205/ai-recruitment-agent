import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'
import type { Role } from './types'

interface ProtectedRouteProps {
  children: ReactNode
  allowedRoles?: Role[]
}

export function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const { user, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <div className="p-8 text-sm text-ink-muted">Đang tải...</div>
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={user.role === 'HR' ? '/hr' : '/candidate'} replace />
  }

  return children
}
