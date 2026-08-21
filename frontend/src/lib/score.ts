// scoring_runs.total_score / job performance averageScore la NUMERIC(6,3) o backend, toi da 3 chu
// so thap phan. JSON khong giu so 0 thua o duoi (65.500 va 65.5 la CUNG mot gia tri JSON - parse
// ra JS number da tu dong bo so 0 thua roi), nhung goi lai .toFixed(3) tren ket qua se VA THEM so
// 0 do, bien 80 (JS number sach) thanh chuoi "80.000" - de doc nham thanh tam muoi nghin theo
// dinh dang so Viet Nam (dau cham la phan cach hang nghin). Number(...).toString() buoc lai qua
// JS number MOT LAN NUA de loai so 0 thua, dong thoi cham dut o toi da 3 chu so (khop dung scale
// cua cot NUMERIC(6,3)) - KHONG cat cung ve 2 chu so: cong thuc tong diem theo trong so co the
// sinh so le that (vd 66.667), cat bot se mat thong tin phan biet hai ung vien sat diem.
//
// Dung CHUNG cho moi noi hien thi diem (CandidatesTable, ScoringRunAuditPanel,
// JobPerformanceTable) - khong lap lai ham nay o tung feature. Chinh vi dung chung, MOI thay doi o
// day anh huong CA BA noi mot luc - giu du hai quy uoc bat buoc sau, dung xoa khi "don dep":
//
// 1. null nghia la CHUA CO diem (chua co luot DONE nao) - KHAC 0 diem THAT. Luon tra "Chưa chấm",
//    KHONG BAO GIO tra "0" cho truong hop null (se lam HR hieu nham ung vien bi cham 0 diem).
//
// 2. Noi goi ham nay TUYET DOI khong duoc tu them className/style to mau theo gia tri tra ve (vd
//    mau khac nhau cho diem cao/thap) - CLAUDE.md va PHASES.md D3 cam ro rang: diem so hien thi
//    trung tinh, viec dat "tot/xau" la cua HR, khong phai giao dien tu goi y bang mau sac.
export function formatScore(score: number | null): string {
  if (score === null) {
    return 'Chưa chấm'
  }
  return Number(score.toFixed(3)).toString()
}
