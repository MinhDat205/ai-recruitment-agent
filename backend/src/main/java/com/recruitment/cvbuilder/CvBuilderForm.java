package com.recruitment.cvbuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// cvbuilder/ - du lieu form CV builder (FR-U12). Luu nguyen vao cv_builder_documents.form_data (JSONB). KHONG them
// truong nhan than nao: anh, ngay sinh, tuoi, gioi tinh, hon nhan, ton giao, dan toc, quoc tich, chieu cao, can nang,
// suc khoe (R-F2). Khoi lien he = DUNG 5 thanh phan cua ResumeParsedPayload.Contact.
// List null -> List.of() o compact constructor (CLAUDE.md muc 4); phan tu null trong list GIU NGUYEN de validator bao
// loi (R-F5), khong loc bot.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvBuilderForm(
        Contact contact,
        String currentTitle,          // chuc danh hien tai (schema v2)
        String locationText,          // khu vuc - chu tu do, bo lam giau khop tinh/thanh (a16)
        String industryCode,          // ma nganh tu danh muc C05; KHONG in ra PDF (O1)
        List<Education> education,
        List<Experience> experience,
        List<String> skills,
        List<Certification> certifications,
        List<Project> projects) {

    public CvBuilderForm {
        education = education == null ? List.of() : education;
        experience = experience == null ? List.of() : experience;
        skills = skills == null ? List.of() : skills;
        certifications = certifications == null ? List.of() : certifications;
        projects = projects == null ? List.of() : projects;
    }

    public record Contact(String fullName, String email, String phone, String address, String linkedin) {}

    public record Education(String school, String degree, String major, String startDate, String endDate, Double gpa) {}

    public record Experience(String company, String title, String startDate, String endDate, Boolean current,
            String description) {}

    public record Certification(String name, String issuer, String issueDate) {}

    public record Project(String name, String description, List<String> technologies) {
        public Project {
            technologies = technologies == null ? List.of() : technologies;
        }
    }
}
