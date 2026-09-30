// Khop CatalogResponse o backend (GET /api/public/catalogs, FR-C05). Co y KHONG co bi danh - bo
// khop chuoi la viec cua backend, frontend chi hien nhan va gui ma.
export interface CatalogItem {
  code: string
  label: string
}

export interface Catalogs {
  industries: CatalogItem[]
  provinces: CatalogItem[]
}
