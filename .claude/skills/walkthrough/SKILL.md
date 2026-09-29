---
name: walkthrough
description: Viết tài liệu giải thích những gì đã làm trong một nhánh, lưu tại docs/walkthrough/. Dùng khi người dùng yêu cầu "viết walkthrough", "giải thích nhánh này đã làm gì", "tổng kết nhánh", hoặc khi vừa hoàn thành một mã FR (cũ hoặc bổ sung) và cần tài liệu bàn giao trước khi merge.
---

# Viết walkthrough cho một nhánh

Đây là tài liệu để người dùng **hiểu** code mà Claude vừa viết, phục vụ review và buổi bảo vệ đồ án.
Người đọc là sinh viên chưa từng đọc codebase này.

## Nơi lưu

`docs/walkthrough/<tên-file>.md`, tên file suy từ tên nhánh:

- Nhánh `feat/`: bỏ tiền tố — `feat/fr-c01-auth` → `docs/walkthrough/fr-c01-auth.md`
- Nhánh `chore/` và `fix/`: giữ tiền tố, đổi `/` thành `-` — `chore/hardening` →
  `docs/walkthrough/chore-hardening.md`

Lấy tên nhánh bằng `git branch --show-current`.

## Cấu trúc bắt buộc — 8 mục

### 1. Mục tiêu
Mã FR này yêu cầu gì, viết lại bằng ngôn ngữ thường, một đoạn. Không copy nguyên văn.
- FR cũ: không copy nguyên văn `docs/SRS.md`.
- FR bổ sung: viết lại từ `docs/features/<nhóm>/<mã>/REQUIREMENT.md` — cùng quy tắc, không chép
  nguyên văn mục "Tóm tắt"/"Mục đích" của file đó.

### 2. Các file đã tạo/sửa
Bảng hai cột: đường dẫn file | vai trò của file trong hệ thống.
Nhóm theo backend/frontend. Không liệt kê file cấu hình vặt.

### 3. Luồng chính
Mô tả **từng bước** một request đi qua hệ thống, từ lúc frontend gửi tới lúc trả response.
Nêu rõ đi qua class nào, phương thức nào, chạm bảng DB nào.

Nếu có nhiều luồng (ví dụ đăng ký và đăng nhập), mô tả riêng từng luồng.
Dùng sơ đồ mermaid nếu luồng có nhánh rẽ.

### 4. Quyết định thiết kế
Mỗi quyết định trình bày ba phần: **đã chọn gì** · **các lựa chọn khác là gì** · **vì sao chọn cái này**.

Đây là mục quan trọng nhất — hội đồng bảo vệ sẽ hỏi đúng những câu này.
Nếu một quyết định đến từ ràng buộc trong SRS/REQUIREMENT.md thì nói rõ mã FR nào.

### 5. Ràng buộc đã thực thi
Bảng: mã FR | ràng buộc | thực thi ở đâu (tên class + tên phương thức, hoặc tên constraint DB).

Nguồn ràng buộc gồm cả ba: ràng buộc trong `docs/SRS.md` (mục "Ghi chú tổng hợp" hoặc mô tả FR),
quy tắc nghiệp vụ trong `REQUIREMENT.md` của FR đó (nếu là FR bổ sung), và mục "Nguyên tắc bổ sung
cho các chức năng mới" ở `docs/SRS.md` khi liên quan.

### 6. Đã kiểm thử gì
Liệt kê những gì đã test và **những gì chưa test**. Không được bỏ trống phần "chưa test".

Thêm bảng đối chiếu "Xong khi": mỗi gạch đầu dòng trong mục "Xong khi" của `REQUIREMENT.md` (FR bổ
sung) hoặc tiêu chí nghiệm thu tương ứng (FR cũ) → cách đã nghiệm thu (tên test + tên file, hoặc
bước test tay cụ thể) → Đạt / Chưa.

**TRƯỚC khi viết phần test tay trong mục này, HỎI người dùng kết quả test tay thật.** Không được
tự ghi "chưa kiểm thử bằng tay" hay tự suy ra lý do/kết quả — lỗi này đã lặp lại ở nhiều nhánh
trước, luôn hỏi trực tiếp trước khi điền phần này.

### 7. Nợ kỹ thuật
Những chỗ làm tạm, giả định đã đặt, thứ cần sửa ở nhánh sau. Nếu không có thì ghi rõ "không có".

### 8. Lệch so với đặc tả
Chỉ áp dụng cho FR bổ sung (có `REQUIREMENT.md`/`UI.md`). Liệt kê MỌI chỗ code cuối cùng khác với
đặc tả đã duyệt ban đầu. Với mỗi chỗ, ghi rõ: đặc tả đã được sửa và **duyệt lại chưa** (kèm ngày
duyệt lại nếu có).

Có chỗ lệch mà đặc tả CHƯA được duyệt lại — ghi nổi bật ngay ở ĐẦU tài liệu (trước mục 1), không
chỉ ở mục 8, để người review thấy ngay.

**Walkthrough KHÔNG tự đổi trạng thái đặc tả** (`ĐÃ DUYỆT` → `ĐÃ HOÀN THÀNH`...) — việc đó thuộc
đợt cuối của skill `fr-implement`, không phải việc của walkthrough. Walkthrough chỉ ghi lại sự
thật đã xảy ra.

Nếu FR cũ (không có REQUIREMENT.md), ghi "Không áp dụng — FR này không có đặc tả REQUIREMENT.md".

## Quy tắc viết

- Viết **tiếng Việt**.
- **Không copy nguyên code vào tài liệu.** Chỉ nêu tên class, tên phương thức, và mô tả việc nó làm.
- Giải thích cho người chưa biết codebase — không giả định người đọc đã hiểu Spring Security hay JPA.
- Trung thực về hạn chế. Tài liệu này để người dùng phát hiện vấn đề, không phải để trình bày thành tích.
- Độ dài hợp lý: 150–300 dòng. Dài hơn nghĩa là đang copy code vào.
- **Test kiểm hệ quả liên nhánh phải được trỏ đích danh.** Nếu nhánh này có một test nằm trong file
  của nhánh này nhưng thực chất bảo vệ một ràng buộc/hành vi thuộc về nhánh khác, nêu rõ **tên test
  + tên file** trong mục "Quyết định thiết kế", kèm câu giải thích vì sao không được xoá khi
  refactor.

## Sau khi viết xong

Đề xuất cho người dùng ba câu hỏi kiểm tra cụ thể cho nhánh này, theo mẫu:
1. Nếu xoá file X thì hỏng cái gì?
2. Dữ liệu đi từ đâu tới đâu, qua những class nào?
3. Vì sao chọn cách A mà không phải cách B?

Thay X, A, B bằng nội dung thật của nhánh, không để chung chung.
