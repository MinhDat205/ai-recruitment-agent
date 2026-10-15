package com.recruitment.aicontext;

// aicontext/ - K1 phan FR-C08. KHONG them thanh phan nao chua diem, rubric, giai thich AI, ghi chu HR, lich su
// trao doi, giay moi, cau tra loi sang loc, ho ten/email/dien thoai lay tu bang users.
//
// R-K1-1 - record rieng, KHONG dung ConversationContext. Du lieu THO: resumeText la raw_text nguyen van - viec cat CV
// (R-A3) va doi < > (R-I3) thuoc CvQaService khi dung prompt; kiem trich dan luon tren chinh resumeText nay.
public record CvQaContext(
        // HR | CANDIDATE
        ContextViewer viewer,
        // resume_parsed_data.raw_text NGUYEN VAN (khong cat, khong thay ky tu)
        String resumeText,
        // chi phia HR; phia ung vien luon null
        JobPosting job) {

    // jobs.title, jobs.description, jobs.requirements - nguyen van, requirements co the null (O1)
    public record JobPosting(String title, String description, String requirements) {
    }
}
