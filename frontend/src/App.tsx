import { Navigate, BrowserRouter, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { RequireCandidateProfileOnboarding } from './features/candidateProfile/RequireCandidateProfileOnboarding'
import { RequireCompany } from './features/companies/RequireCompany'
import { CandidateApplicationsPage } from './pages/CandidateApplicationsPage'
import { CandidateHomePage } from './pages/CandidateHomePage'
import { CandidateJobListPage } from './pages/CandidateJobListPage'
import { CandidateNotificationsPage } from './pages/CandidateNotificationsPage'
import { CandidateOnboardingPage } from './pages/CandidateOnboardingPage'
import { CandidateProfilePage } from './pages/CandidateProfilePage'
import { CompanyProfilePage } from './pages/CompanyProfilePage'
import { CvImprovementSuggestionsPage } from './pages/CvImprovementSuggestionsPage'
import { HrCandidatesPage } from './pages/HrCandidatesPage'
import { HrHomePage } from './pages/HrHomePage'
import { HrJobCreatePage } from './pages/HrJobCreatePage'
import { HrJobEditPage } from './pages/HrJobEditPage'
import { HrJobListPage } from './pages/HrJobListPage'
import { HrNotificationsPage } from './pages/HrNotificationsPage'
import { JobApplyPage } from './pages/JobApplyPage'
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
          {/* FR-U14 R-O2 - /candidate/onboarding KHONG bi RequireCandidateProfileOnboarding boc (se
              tu dieu huong vong lap), nen dat RIENG ngoai nhom /candidate/* ben duoi. */}
          <Route
            path="/candidate/onboarding"
            element={
              <ProtectedRoute allowedRoles={['CANDIDATE']}>
                <CandidateOnboardingPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/jobs/:id/apply"
            element={
              <ProtectedRoute allowedRoles={['CANDIDATE']}>
                <JobApplyPage />
              </ProtectedRoute>
            }
          />
          {/* Moi route /candidate/* (tru /candidate/onboarding o tren) deu can qua
              RequireCandidateProfileOnboarding (FR-U14 R-O2) - giong khuon RequireCompany o nhom HR
              ben duoi. */}
          <Route
            element={
              <ProtectedRoute allowedRoles={['CANDIDATE']}>
                <RequireCandidateProfileOnboarding>
                  <Outlet />
                </RequireCandidateProfileOnboarding>
              </ProtectedRoute>
            }
          >
            <Route path="/candidate" element={<CandidateJobListPage />} />
            <Route path="/candidate/dashboard" element={<CandidateHomePage />} />
            <Route path="/candidate/profile" element={<CandidateProfilePage />} />
            <Route path="/candidate/applications" element={<CandidateApplicationsPage />} />
            <Route path="/candidate/notifications" element={<CandidateNotificationsPage />} />
            <Route
              path="/candidate/resumes/:id/improvement-suggestions"
              element={<CvImprovementSuggestionsPage />}
            />
          </Route>
          {/* Moi route /hr/* MOI mac dinh dat trong nhom nay (can ho so cong ty). Chi route nao
              chac chan dung duoc khi HR chua co cong ty moi dat ngoai nhom, nhu /hr/company va
              /hr/notifications ben duoi. */}
          <Route
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <RequireCompany>
                  <Outlet />
                </RequireCompany>
              </ProtectedRoute>
            }
          >
            <Route path="/hr" element={<HrHomePage />} />
            <Route path="/hr/candidates" element={<HrCandidatesPage />} />
            <Route path="/hr/jobs" element={<HrJobListPage />} />
            <Route path="/hr/jobs/new" element={<HrJobCreatePage />} />
            <Route path="/hr/jobs/:id/edit" element={<HrJobEditPage />} />
          </Route>
          <Route
            path="/hr/company"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <CompanyProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/hr/notifications"
            element={
              <ProtectedRoute allowedRoles={['HR']}>
                <HrNotificationsPage />
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