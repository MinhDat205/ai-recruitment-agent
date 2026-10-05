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
        className="h-11 rounded-md bg-m3-tertiary px-6 text-sm font-medium text-m3-on-tertiary"
      >
        Ứng tuyển
      </button>
    )
  }

  return (
    <button
      type="button"
      data-job-id={jobId}
      onClick={() => navigate(`/jobs/${jobId}/apply`)}
      className="h-11 rounded-md bg-m3-tertiary px-6 text-sm font-medium text-m3-on-tertiary"
    >
      Ứng tuyển
    </button>
  )
}
