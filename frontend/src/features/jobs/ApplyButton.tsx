import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function ApplyButton({ jobId }: { jobId: string }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  if (user?.role === 'HR') {
    return null
  }

  if (!user) {
    return (
      <button
        type="button"
        data-job-id={jobId}
        onClick={() => navigate('/login', { state: { from: location } })}
        className="h-11 rounded-md bg-accent px-6 text-sm font-medium text-white"
      >
        Ứng tuyển
      </button>
    )
  }

  // Candidate da dang nhap: route nop don that thuoc FR khac, chua ton tai o A2.
  return (
    <button
      type="button"
      disabled
      data-job-id={jobId}
      title="Sắp ra mắt"
      className="h-11 cursor-not-allowed rounded-md bg-accent/50 px-6 text-sm font-medium text-white"
    >
      Ứng tuyển (sắp ra mắt)
    </button>
  )
}
