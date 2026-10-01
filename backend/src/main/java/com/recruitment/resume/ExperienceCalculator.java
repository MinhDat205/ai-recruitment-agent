package com.recruitment.resume;

import com.recruitment.catalog.CatalogTextNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// So thang kinh nghiem (FR-C05 R-E1..R-E8) - class THUAN, xac dinh, khong Spring, khong goi AI. Dau vao
// chi la startDate/endDate cua data.experience[] (co o ca v1 va v2); hoc van, du an khong tinh.
// KHONG dung double/float o bat ky buoc nao: quy doi nam bang BigDecimal HALF_UP (R-E8) - 0,25 va 0,75
// nam dung bien lam tron.
public final class ExperienceCalculator {

    // R-E2: moc tham chieu la THANG cua thoi diem tinh, theo gio Viet Nam (00:30 ngay 01/10 gio VN van
    // la 30/09 theo UTC - lay theo UTC se lech mot thang).
    public static final ZoneId REFERENCE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    // R-E4: tu chi "dang lam", so sau chuan hoa R-M1a (bo dau, chu thuong).
    private static final Set<String> PRESENT_WORDS =
            Set.of("hien tai", "hien nay", "nay", "den nay", "present", "now", "current");

    // R-E3 - moi dang mot pattern, khop TOAN BO chuoi (khong tim chuoi con: "2019 - 06/2020" khong duoc
    // doc thanh 06/2020).
    private static final Pattern MONTH_SLASH_YEAR = Pattern.compile("(\\d{1,2})/(\\d{4})");
    private static final Pattern MONTH_DASH_OR_DOT_YEAR = Pattern.compile("(\\d{2})[-.](\\d{4})");
    private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})[-/](\\d{2})");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("(\\d{2})([/-])(\\d{2})\\2(\\d{4})");
    private static final Pattern VN_MONTH_SLASH_YEAR = Pattern.compile("thang (\\d{1,2})/(\\d{4})");
    private static final Pattern VN_MONTH_WORD_YEAR = Pattern.compile("thang (\\d{1,2}) nam (\\d{4})");
    private static final Pattern EN_MONTH_NAME_YEAR = Pattern.compile("([a-z]+) (\\d{4})");

    private static final Map<String, Integer> EN_MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("january", 1),
            Map.entry("feb", 2), Map.entry("february", 2),
            Map.entry("mar", 3), Map.entry("march", 3),
            Map.entry("apr", 4), Map.entry("april", 4),
            Map.entry("may", 5),
            Map.entry("jun", 6), Map.entry("june", 6),
            Map.entry("jul", 7), Map.entry("july", 7),
            Map.entry("aug", 8), Map.entry("august", 8),
            Map.entry("sep", 9), Map.entry("september", 9),
            Map.entry("oct", 10), Map.entry("october", 10),
            Map.entry("nov", 11), Map.entry("november", 11),
            Map.entry("dec", 12), Map.entry("december", 12));

    private ExperienceCalculator() {
    }

    // months null <=> khong co muc nao duoc tinh (ke ca danh sach rong) - R-E7: KHONG tra 0.
    public record Result(Integer months, int countedEntries, int skippedEntries) {
    }

    public static YearMonth referenceMonth(Instant computedAt) {
        return YearMonth.from(computedAt.atZone(REFERENCE_ZONE));
    }

    public static Result compute(List<ResumeParsedPayload.Experience> entries, YearMonth refMonth) {
        List<long[]> ranges = new ArrayList<>();
        int skipped = 0;
        for (ResumeParsedPayload.Experience entry : entries) {
            long[] range = countedRange(entry, refMonth);
            if (range == null) {
                skipped++;
            } else {
                ranges.add(range);
            }
        }
        if (ranges.isEmpty()) {
            return new Result(null, 0, skipped);
        }
        return new Result(unionMonthCount(ranges), ranges.size(), skipped);
    }

    // R-E8: thang -> nam, mot chu so thap phan, HALF_UP. Frontend KHONG tu quy doi.
    public static BigDecimal toYears(int months) {
        return BigDecimal.valueOf(months).divide(MONTHS_PER_YEAR, 1, RoundingMode.HALF_UP);
    }

    // R-E5: tinh khi doc duoc ca hai moc va start <= end <= refMonth; nguoc lai null (bo qua). Khong
    // quy uoc thang cho moc chi co nam, khong tu dien "hien tai" khi thieu endDate.
    private static long[] countedRange(ResumeParsedPayload.Experience entry, YearMonth refMonth) {
        if (entry == null) {
            return null;
        }
        YearMonth start = parseMonth(entry.startDate());
        if (start == null) {
            return null;
        }
        YearMonth end = parseEnd(entry.endDate(), refMonth);
        if (end == null || start.isAfter(end) || end.isAfter(refMonth)) {
            return null;
        }
        return new long[] {monthIndex(start), monthIndex(end)};
    }

    private static YearMonth parseEnd(String endDate, YearMonth refMonth) {
        String normalized = CatalogTextNormalizer.normalizeText(endDate);
        if (normalized != null && PRESENT_WORDS.contains(normalized)) {
            return refMonth;
        }
        return parseMonth(endDate);
    }

    // R-E3: doc mot moc -> (nam, thang) hoac null. Chi chuan hoa R-M1a (normalizeText), KHONG R-M1b:
    // buoc gach noi bien "01-2020" thanh "01 - 2020". Tu "dang lam" o startDate khong khop pattern nao
    // nen tu nhien la null (R-E4).
    static YearMonth parseMonth(String raw) {
        String text = CatalogTextNormalizer.normalizeText(raw);
        if (text == null) {
            return null;
        }
        Matcher m;
        if ((m = MONTH_SLASH_YEAR.matcher(text)).matches() || (m = MONTH_DASH_OR_DOT_YEAR.matcher(text)).matches()) {
            return toYearMonth(m.group(2), m.group(1));
        }
        if ((m = YEAR_MONTH.matcher(text)).matches()) {
            return toYearMonth(m.group(1), m.group(2));
        }
        if ((m = DAY_MONTH_YEAR.matcher(text)).matches()) {
            return toYearMonth(m.group(4), m.group(3));
        }
        if ((m = VN_MONTH_SLASH_YEAR.matcher(text)).matches() || (m = VN_MONTH_WORD_YEAR.matcher(text)).matches()) {
            return toYearMonth(m.group(2), m.group(1));
        }
        if ((m = EN_MONTH_NAME_YEAR.matcher(text)).matches()) {
            Integer month = EN_MONTHS.get(m.group(1));
            return month == null ? null : toYearMonth(m.group(2), String.valueOf(month));
        }
        return null;
    }

    // Thang ngoai 1..12 -> null. Nam luon 4 chu so (pattern da chan nam 2 chu so).
    private static YearMonth toYearMonth(String year, String month) {
        int monthValue = Integer.parseInt(month);
        if (monthValue < 1 || monthValue > 12) {
            return null;
        }
        return YearMonth.of(Integer.parseInt(year), monthValue);
    }

    private static long monthIndex(YearMonth yearMonth) {
        return yearMonth.getYear() * 12L + (yearMonth.getMonthValue() - 1);
    }

    // R-E6: moi muc = tap thang tu start toi end GOM CA HAI DAU; ket qua = so thang cua HOP cac tap
    // (trung/long nhau chi tinh mot lan). Hai khoang lien ke (12/2020 va 01/2021) gop duoc nhung dem
    // van dung vi khong co thang nao trung.
    private static int unionMonthCount(List<long[]> ranges) {
        ranges.sort(Comparator.comparingLong(range -> range[0]));
        long total = 0;
        long currentStart = ranges.get(0)[0];
        long currentEnd = ranges.get(0)[1];
        for (int i = 1; i < ranges.size(); i++) {
            long[] range = ranges.get(i);
            if (range[0] <= currentEnd + 1) {
                currentEnd = Math.max(currentEnd, range[1]);
            } else {
                total += currentEnd - currentStart + 1;
                currentStart = range[0];
                currentEnd = range[1];
            }
        }
        total += currentEnd - currentStart + 1;
        return Math.toIntExact(total);
    }
}
