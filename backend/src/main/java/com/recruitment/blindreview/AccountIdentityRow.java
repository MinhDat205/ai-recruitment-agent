package com.recruitment.blindreview;

import java.util.UUID;

// FR-H16 R-M1 - interface projection: gia tri danh tinh cua tai khoan ung vien theo don (IdentityMaskSource).
public interface AccountIdentityRow {

    UUID getApplicationId();

    String getFullName();

    String getEmail();

    String getPhone();
}
