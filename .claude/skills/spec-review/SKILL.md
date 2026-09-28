---
name: spec-review
description: Soát bản nháp REQUIREMENT.md/UI.md của một FR bổ sung, đối chiếu với code, schema, SRS.md, CLAUDE.md và UI_GUIDE.md TRƯỚC khi người dùng duyệt. Dùng khi người dùng nói "soát đặc tả FR-xxx", "review REQUIREMENT", "kiểm tra đặc tả trước khi duyệt".
---

# Soát đặc tả trước khi duyệt

## Quy tắc cứng

**CHỈ ĐỌC VÀ BÁO CÁO.** Không sửa `REQUIREMENT.md`/`UI.md` của FR đang soát, không tự đổi dòng
trạng thái (`CHƯA ĐẶC TẢ`/`ĐÃ DUYỆT`/`ĐÃ HOÀN THÀNH`), không viết code. Việc duyệt là của người
dùng; skill này chỉ chuẩn bị dữ liệu để người dùng duyệt đúng.

## Các mục soát

1. **Đủ mục bắt buộc**: `REQUIREMENT.md` phải có đủ các mục theo CLAUDE.md §6 tiểu mục "Quy trình
   cho chức năng bổ sung" (Mục đích · Luồng người dùng · Quy tắc nghiệp vụ · Dữ liệu & quyền truy
   cập · AI (nếu có) · Ngoài phạm vi · Xong khi · AI hay làm sai); `UI.md` phải có đủ 10 mục theo
   `docs/UI_GUIDE.md` mục 8.

2. **Khẳng định kỹ thuật**: mọi câu trong đặc tả nêu tên bảng, cột, enum, endpoint, class, hay hành
   vi hiện có của hệ thống → đối chiếu với code/migration thật, ghi rõ tên class/method/bảng đã
   đối chiếu và kết luận **Đúng / Sai / Không tìm thấy**.

3. **Phụ thuộc**: dòng "Phụ thuộc" trong `REQUIREMENT.md` phải khớp danh sách phụ thuộc ở
   `docs/SRS.md` (bảng "Chức năng bổ sung" ở mục 1/2/3); kiểm trạng thái từng phụ thuộc ở mục 0
   `docs/SRS.md` — phụ thuộc chưa "Đã hoàn thành" phải được nêu ra như một rủi ro, không im lặng.

4. **Route**: mọi route nêu trong `UI.md` phải có trong bảng "Bản đồ màn hình" (`docs/UI_GUIDE.md`
   mục 7) — thiếu thì đây là lỗi cần sửa trước khi duyệt (bảng đó phải được cập nhật trước, không
   phải UI.md tự thêm route ngoài bảng). Nếu FR đụng tới màn hình cũ (route đã có, không phải
   ★Mới), UI.md phải nêu rõ đây là màn hình cũ bị mở rộng, không phải màn hình mới.

5. **Xung đột nguyên tắc**: đối chiếu đặc tả với CLAUDE.md §2 (nguyên tắc bất di bất dịch) và §7
   (ranh giới), mục "Nguyên tắc bổ sung cho các chức năng mới" ở `docs/SRS.md`, và mục 4 "Ràng
   buộc riêng của dự án" ở `docs/UI_GUIDE.md`. Kiểm cụ thể: FR có gọi LLM/embedding đồng bộ mà
   không thuộc danh sách ngoại lệ K3 (CLAUDE.md §7) không; FR có đúng là nơi "Xây lần đầu" một
   thành phần K1-K4 theo CLAUDE.md §3d không, và nếu FR chỉ "Dùng bởi" một K thì K đó đã được FR
   nào khác xây xong chưa (chưa xong → phải nêu là rủi ro chặn tiến độ, không phải lỗi đặc tả).

6. **"Xong khi" có kiểm thử được không**: từng tiêu chí trong mục "Xong khi" phải đo được (có số
   cụ thể, có trạng thái cụ thể, hoặc có bước tái hiện cụ thể) — chỉ ra chính xác tiêu chí nào mơ
   hồ (ví dụ "hiển thị hợp lý", "trải nghiệm tốt" không đo được) và đề xuất cách viết lại cụ thể
   hơn, không tự sửa file.

7. **Mâu thuẫn giữa các FR**: đọc `REQUIREMENT.md` của các FR khác đã `ĐÃ DUYỆT`/`ĐÃ HOÀN THÀNH`
   có liên quan (theo cột "Dùng bởi"/"Xây lần đầu" ở CLAUDE.md §3d, hoặc theo phụ thuộc ngược ở
   `docs/SRS.md`) — có mô tả nào trái ngược với FR đang soát không (ví dụ hai FR cùng mô tả khác
   nhau về cùng một hành vi/route/schema).

## Mẫu báo cáo

```
## Soát đặc tả — FR-<mã>

### Sai với code (phải sửa trước khi duyệt)
- <mục soát N> <mô tả> — code thật: `<Class.method>` / migration `<Vxx>`
  Đề xuất sửa: <cụ thể>

### Thiếu hoặc mơ hồ
- ...

### Cần người dùng quyết định
- ...

### Đã đối chiếu, khớp
- Mục soát 1, 3, 4 ✓
```

Nếu không phát hiện vấn đề nào ở một nhóm, vẫn liệt kê rõ mục nào đã đối chiếu và khớp — không bỏ
trống, không chỉ nói "ổn".
