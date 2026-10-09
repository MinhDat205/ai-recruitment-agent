package com.recruitment.messaging;

import java.time.Instant;
import java.util.UUID;

// Interface projection cho native query hop thu (M5) - dung chung hai phia. counterpartName: phia HR la ho
// ten ung vien, phia ung vien la TEN CONG TY (R-I2) - chon o cau SQL, khong bao gio la ho ten/email HR.
// getApplicationStatus()/getLastSenderRole() la String (khong phai enum): Spring Data khong tu convert
// String -> enum cho projection cua native query (quy uoc CandidateSearchRow).
public interface ConversationRow {

    UUID getApplicationId();

    UUID getJobId();

    String getJobTitle();

    String getCounterpartName();

    String getApplicationStatus();

    Instant getLastMessageAt();

    String getLastBody();

    String getLastSenderRole();

    Boolean getLastHasAttachment();

    Long getUnreadCount();
}
