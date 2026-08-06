# Hướng dẫn giao diện — AI Recruitment Agent

Phong cách tham chiếu: job board Việt Nam (CareerViet, VietnamWorks, TopCV).
Đặc trưng: nền sáng, mật độ thông tin cao, bố cục dạng thẻ, xanh dương làm màu thương hiệu,
xanh lá cho hành động chính, cam cho tính khẩn.

> **Ranh giới:** mô phỏng *quy ước bố cục và hệ màu*, không sao chép logo, tên thương hiệu,
> hình ảnh hay file CSS của bất kỳ trang nào. Dự án dùng tên và logo riêng.

---

## 1. Design token

Khai báo một lần trong `frontend/src/index.css`, mọi component dùng qua biến — không hardcode hex.

```css
@import "tailwindcss";

@theme {
  --color-brand:        #0078C9;
  --color-brand-dark:   #1E5C8B;
  --color-brand-light:  #E6F2FA;
  --color-accent:       #1AC639;
  --color-accent-dark:  #008C45;
  --color-warning:      #FF5B00;
  --color-danger:       #E11B3E;

  --color-ink:          #1F2937;
  --color-ink-muted:    #6B7280;
  --color-line:         #E7E7E9;
  --color-surface:      #FFFFFF;
  --color-canvas:       #F0F0F0;

  --radius-card:  8px;
  --radius-badge: 4px;
}
```

Dùng trong component: `bg-brand`, `text-ink-muted`, `border-line`, `rounded-[--radius-card]`.

## 2. Typography

- Font: **Inter** hoặc **Be Vietnam Pro** (hỗ trợ dấu tiếng Việt tốt, không bị vỡ dấu như nhiều font Latin).
- Tiêu đề trang 24px/600 · tiêu đề section 20px/600 · tiêu đề card 16px/500 · body 14px/400 · meta 13px/400.
- Chỉ dùng hai trọng lượng: 400 và 500/600. Không dùng 700 trở lên.
- Tiêu đề tiếng Việt viết hoa chữ đầu câu, không viết hoa mọi từ.

## 3. Layout

**Trang công khai / ứng viên**
```
Header cố định   : logo trái · menu ngang · ô tìm kiếm · nút Đăng nhập/Đăng ký phải
Hero             : nền brand, ô tìm kiếm lớn 3 trường (từ khoá · địa điểm · ngành nghề) + nút tìm
Section lặp lại  : tiêu đề canh giữa + gạch chân ngắn màu brand, lưới thẻ, link "Xem thêm →" bên phải
Footer           : 4 cột link + thông tin liên hệ, nền tối
```

**Trang HR (quản trị)**
```
Sidebar trái cố định 240px : Dashboard · Tin tuyển dụng · Ứng viên · Rubric · Hồ sơ công ty
Topbar                     : breadcrumb · chuông thông báo · avatar
Nội dung                   : bảng dữ liệu dày, bộ lọc trên đầu, phân trang dưới
```

Không dùng hero ở trang HR. Không dùng sidebar ở trang ứng viên.

Container `max-width: 1200px`, padding ngang 16px, lưới 12 cột, gutter 24px.
Breakpoint: `sm 640` · `md 768` · `lg 1024` · `xl 1280`.

## 4. Component chuẩn

### Card việc làm

```
┌──────────────────────────────────────────────────────┐
│ ┌────┐  Chuyên viên phân tích dữ liệu        [Mới]   │
│ │LOGO│  CÔNG TY TNHH ABC                             │
│ │80px│  💰 15 - 25 triệu   📍 Hồ Chí Minh            │
│ └────┘  [Python] [SQL] [Remote]      Hạn: 30/09      │
└──────────────────────────────────────────────────────┘
```

- Logo vuông 80px, bo 4px, viền `--color-line`, `object-fit: contain` (logo công ty tỉ lệ rất khác nhau).
- Tiêu đề `line-clamp-2`, hover đổi sang `--color-brand`.
- Lương màu `--color-accent-dark`, không phải màu chữ thường — đây là thông tin người dùng quét đầu tiên.
- Tag: nền `--color-brand-light`, chữ `--color-brand`, bo 4px, 12px.
- Toàn thẻ là vùng bấm được, `hover:shadow-sm hover:border-brand`.
- Lưới: 2 cột `lg`, 1 cột `md` trở xuống.

### Nút

| Loại | Style |
|---|---|
| Chính | nền `--color-brand`, chữ trắng |
| Ứng tuyển | nền `--color-accent`, chữ trắng |
| Phụ | viền `--color-brand`, chữ `--color-brand`, nền trong suốt |
| Nguy hiểm | viền `--color-danger`, chữ `--color-danger` |

Cao 40px, padding ngang 20px, bo 6px. Nút trong bảng cao 32px.

### Badge trạng thái đơn ứng tuyển

Đúng 5 trạng thái theo FR-U03, dùng màu **trung tính về mặt phán quyết**:

| Trạng thái | Màu nền / chữ |
|---|---|
| Chờ duyệt | `#F3F4F6` / `#4B5563` |
| Đã mời phỏng vấn | `#E6F2FA` / `#0078C9` |
| Trúng tuyển | `#E8F8EC` / `#008C45` |
| Bị từ chối | `#FDECEF` / `#E11B3E` |
| Đã rút đơn | `#F3F4F6` / `#9CA3AF` |

## 5. Ràng buộc riêng của dự án

Đây là phần khác biệt so với một job board thông thường, và là phần dễ làm sai nhất.

**Màn hình xếp hạng ứng viên (FR-H05, FR-H07)**
- Hiển thị: thứ hạng, tổng điểm, điểm từng tiêu chí, nút mở giải thích.
- **Không** dùng thang màu đỏ-vàng-xanh cho tổng điểm. Không có nhãn "Phù hợp cao / Cần xem xét /
  Không phù hợp". Không có icon ✓ ✗. Những thứ này là phán quyết trá hình, vi phạm FR-H07.
- Điểm hiển thị dạng số và thanh tiến trình đơn sắc (`--color-brand`), không đổi màu theo ngưỡng.

**Màn hình giải thích điểm (FR-H06)**
- Mỗi tiêu chí là một khối gập/mở được. Mở ra phải thấy đoạn trích nguyên văn từ CV làm evidence.
- Trích dẫn hiển thị với viền trái `--color-brand`, nền `--color-brand-light`, font chữ thường.
- Không có điểm nào hiển thị mà không mở ra được evidence.

**Ô consent khi ứng tuyển (FR-U02)**
- Checkbox không tick sẵn. Nút "Nộp đơn" disabled cho tới khi tick.
- Nội dung ghi rõ CV sẽ được AI phân tích và chấm điểm để hỗ trợ HR đánh giá.

## 6. Trạng thái rỗng và tải

- Mọi danh sách phải có empty state: một dòng chữ `--color-ink-muted` + một hành động gợi ý.
- Card đang tải dùng skeleton xám, không dùng spinner toàn trang.
- Job nền (parse CV, chấm điểm) hiển thị trạng thái theo `parse_status` / `scoring_runs.status`,
  không để người dùng nhìn màn hình trắng chờ.

## 7. Khả năng tiếp cận

- Tỉ lệ tương phản tối thiểu 4.5:1 cho chữ. `--color-warning` trên nền trắng **không đạt** — chỉ dùng
  làm màu nền badge với chữ trắng, không dùng làm màu chữ.
- Mọi icon-only button phải có `aria-label`.
- Không truyền đạt thông tin chỉ bằng màu — badge trạng thái luôn kèm chữ.