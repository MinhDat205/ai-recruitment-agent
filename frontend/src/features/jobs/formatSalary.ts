// FR-U08 R-C4 - ham dinh dang luong DUY NHAT cua tin tuyen dung, dung chung cho the viec lam (JobCard),
// trang tin cong khai (/jobs/:id), danh sach tin phia HR (/hr/jobs) va trang chi tiet don cua ung vien. Than
// ham chuyen nguyen tu PublicJobDetailPage - ket qua hien thi khong doi. Khong co luong -> null; chu hien thi
// thay the ("—", "Không công bố"...) la viec cua noi goi.
export function formatSalary(job: {
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
}): string | null {
  if (job.salaryMin == null && job.salaryMax == null) {
    return null
  }
  const currency = job.salaryCurrency ?? 'VND'
  const format = (value: number) => value.toLocaleString('vi-VN')
  if (job.salaryMin != null && job.salaryMax != null) {
    return `${format(job.salaryMin)} - ${format(job.salaryMax)} ${currency}`
  }
  const value = job.salaryMin ?? job.salaryMax
  return value != null ? `${format(value)} ${currency}` : null
}
