package com.recruitment.resume;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Schema co dinh cho cot resume_parsed_data.data (JSONB) - dung record, khong Map tu do, de D2/F1
// doc lai co kieu ro rang thay vi tu doan cau truc. @JsonIgnoreProperties o moi record: khi co
// resume-parse-v2 voi schema khac, cac hang cu ghi bang schema v1 van phai doc lai duoc qua
// Hibernate (JSONB -> entity) ma khong throw.
// Moi field so dung wrapper type (Integer/Double), khong dung primitive: Jackson gan 0 cho
// primitive khi field vang mat trong JSON, xoa mat kha nang phan biet "LLM khong tim thay thong
// tin nay" voi "gia tri that la 0" - anh huong truc tiep toi D2 dung du lieu nay lam evidence.
//
// contact CO THE null - khi LLM khong trich duoc bat ky thong tin lien he nao tu CV, day la mot
// trang thai co that, khac han voi "list rong". KHONG tu tao Contact rong voi moi field null de
// tranh null o day - lam vay chi day NPE sang tang khac. Noi goi (D2, F1, F2) PHAI tu kiem tra
// null truoc khi doc payload.contact().
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeParsedPayload(
        Contact contact,
        List<Education> education,
        List<Experience> experience,
        List<String> skills,
        List<Certification> certifications,
        List<Project> projects) {

    // Compact constructor: khoi CV khong co (vd ung vien khong co certifications) khien LLM tra ve
    // field null hoac vang mat trong JSON - Jackson truyen thang null vao constructor thay vi list
    // rong. Chan tai day, khong de D2 phai tu kiem tra null truoc khi doc payload.projects()... o
    // moi noi goi.
    public ResumeParsedPayload {
        education = education == null ? List.of() : education;
        experience = experience == null ? List.of() : experience;
        skills = skills == null ? List.of() : skills;
        certifications = certifications == null ? List.of() : certifications;
        projects = projects == null ? List.of() : projects;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(String fullName, String email, String phone, String address, String linkedin) {
    }

    // startDate/endDate la String, khong LocalDate: LLM tra ve tu CV that co the chi co
    // thang/nam ("06/2020"), hoac "Present"/"Hien tai" cho cong viec dang lam - ep ve LocalDate se
    // mat thong tin hoac parse fail voi CV that.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(
            String school, String degree, String major, String startDate, String endDate, Double gpa) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Experience(
            String company, String title, String startDate, String endDate, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Certification(String name, String issuer, String issueDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String name, String description, List<String> technologies) {
        // Cung lop mine null-vs-empty-list nhu o record ngoai (xem compact constructor tren) -
        // technologies cung la List<> co the vang mat trong JSON LLM tra ve.
        public Project {
            technologies = technologies == null ? List.of() : technologies;
        }
    }
}
