// Hien thi nganh nghe / tinh thanh cua Job theo FR-C05 R-J7 - MOT cho cho moi man hinh.
interface JobCatalogView {
  categoryCode: string | null
  categoryLabel: string | null
  locationCode: string | null
  locationLabel: string | null
  legacyCategory: string | null
  legacyLocation: string | null
  workMode: string | null
}

export const REMOTE_WITHOUT_PROVINCE_TEXT = 'Làm từ xa'

// Dong goi y duoi o danh muc trong form Job (UI.md muc 7).
export const CATEGORY_HELPER_TEXT = 'Bắt buộc khi mở tin.'
export const LOCATION_HELPER_TEXT = 'Bắt buộc khi mở tin, trừ hình thức Làm từ xa.'

// Nhan cua ma -> gia tri cu nguyen van -> null (thieu han).
export function jobCategoryText(job: JobCatalogView): string | null {
  return job.categoryLabel ?? job.legacyCategory ?? null
}

// Nhan cua ma -> gia tri cu nguyen van -> "Lam tu xa" (REMOTE khong co tinh) -> null (thieu han).
export function jobLocationText(job: JobCatalogView): string | null {
  if (job.locationLabel) {
    return job.locationLabel
  }
  if (job.legacyLocation) {
    return job.legacyLocation
  }
  return job.workMode === 'REMOTE' ? REMOTE_WITHOUT_PROVINCE_TEXT : null
}

// "Chua chuan hoa" (R-J2): khong co ma nhung con gia tri cu. Backend chi tra legacy* khi dung la vay.
export function isLocationUnnormalized(job: JobCatalogView): boolean {
  return job.locationCode == null && job.legacyLocation != null
}

export function isCategoryUnnormalized(job: JobCatalogView): boolean {
  return job.categoryCode == null && job.legacyCategory != null
}

// R-J3: dieu kien mo tin - co nganh nghe; co tinh/thanh tru khi REMOTE. Chi de hien canh bao; chot chan
// that la backend (409 JOB_CATALOG_INCOMPLETE).
export function isCatalogComplete(job: Pick<JobCatalogView, 'categoryCode' | 'locationCode' | 'workMode'>): boolean {
  return job.categoryCode != null && (job.locationCode != null || job.workMode === 'REMOTE')
}
