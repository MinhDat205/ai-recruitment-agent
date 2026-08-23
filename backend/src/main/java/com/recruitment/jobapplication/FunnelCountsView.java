package com.recruitment.jobapplication;

// Interface projection cho native query (JobApplicationRepository.countFunnelForCompany, F3
// FR-H08) - mot dong duy nhat, khong GROUP BY, khong co cot enum nen khong dinh loi
// String-vs-enum cua StatusCountView. everInvited/everHired dem theo "DA TUNG dat trang thai"
// doc tu application_status_history.to_status (KHONG phai job_applications.status hien tai) -
// xem comment tren method trong JobApplicationRepository.
public interface FunnelCountsView {

    long getTotalApplications();

    long getEverInvited();

    long getEverHired();
}
