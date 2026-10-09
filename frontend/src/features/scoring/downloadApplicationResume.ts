import { downloadApplicationResumeRequest } from './api'

// FR-H09 R-C4 - tai CV goc cua don dang blob, dung CHUNG cho danh sach theo Job (ApplicationsTab) va
// trang ho so don. Tach nguyen van tu ApplicationsTab.handleDownloadResume.
//
// Blob download qua axios (KHONG phai <a href>/window.open truc tiep toi URL backend) - endpoint
// yeu cau header Authorization (Bearer token), the <a href> thuong khong gan duoc header nay vao
// request (mau y het handleDownload trong features/resumes/ResumeList.tsx, cung ly do). Loi tra
// ve khi responseType 'blob' cung la Blob (khong phai JSON da parse) nen extractErrorMessage o noi
// goi se luon roi ve cau fallback - gioi han da biet va chap nhan duoc, giong y het ResumeList.
//
// Dieu kien khoa nut KHONG nam o day (R-V2): danh sach khoa khi CV chua DONE, trang ho so don thi
// khong - moi noi goi tu quyet. Loi nem ra cho noi goi tu hien thong bao.
export async function downloadApplicationResume(applicationId: string): Promise<void> {
  const { blob, fileName } = await downloadApplicationResumeRequest(applicationId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
