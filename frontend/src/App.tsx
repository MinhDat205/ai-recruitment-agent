import { Navigate, BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { CandidateHomePage } from './pages/CandidateHomePage'
import { CandidateProfilePage } from './pages/CandidateProfilePage'
import { CompanyProfilePage } from './pages/CompanyProfilePage'
import { HrHomePage } from './pages/HrHomePage'
import { HrJobCreatePage } from './pages/HrJobCreatePage'
import { HrJobEditPage } from './pages/HrJobEditPage'
import { HrJobListPage } from './pages/HrJobListPage'
import { LoginPage } from './pages/LoginPage'
import { PublicCompanyProfilePage } from './pages/PublicCompanyProfilePage'
import { PublicJobDetailPage } from './pages/PublicJobDetailPage'
import { PublicJobListPage } from './pages/PublicJobListPage'
import { RegisterPage } from './pages/RegisterPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<PublicJobListPage />} />
          <Route path="/jobs/:id" element={<PublicJobDetailPage />} />
          <Route path="/companies/:id" element={<PublicCompanyProfilePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/candidate"
            element={
              <ProtectedRoute allowedRoles={['CANDIDATE']}>
                <CandidateHomePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/candidate/profile"
            element={
              <ProtectedRoute allowedRoles={['CANDIDATE']}>
                <CandidateProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/hr"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <HrHomePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/hr/company"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <CompanyProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/hr/jobs"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <HrJobListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/hr/jobs/new"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <HrJobCreatePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/hr/jobs/:id/edit"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <HrJobEditPage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App