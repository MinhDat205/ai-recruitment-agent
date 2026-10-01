package com.recruitment.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// REQUIREMENT FR-C05 muc 7.8 (khach goi duoc) va 7.9 (du 34 + 24, dung sort_order, khong lo bi danh).
// Thu tu ma chep doc lap voi V8 de bat loi sort_order.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogPublicControllerIntegrationTest {

    private static final List<String> PROVINCE_ORDER = List.of(
            "HA_NOI", "HO_CHI_MINH", "HAI_PHONG", "DA_NANG", "CAN_THO", "HUE", "AN_GIANG", "BAC_NINH", "CA_MAU",
            "CAO_BANG", "DAK_LAK", "DIEN_BIEN", "DONG_NAI", "DONG_THAP", "GIA_LAI", "HA_TINH", "HUNG_YEN",
            "KHANH_HOA", "LAI_CHAU", "LAM_DONG", "LANG_SON", "LAO_CAI", "NGHE_AN", "NINH_BINH", "PHU_THO",
            "QUANG_NGAI", "QUANG_NINH", "QUANG_TRI", "SON_LA", "TAY_NINH", "THAI_NGUYEN", "THANH_HOA",
            "TUYEN_QUANG", "VINH_LONG");

    private static final List<String> INDUSTRY_ORDER = List.of(
            "IT_SOFTWARE", "IT_HARDWARE_NETWORK", "ACCOUNTING_AUDIT", "FINANCE_BANKING", "INSURANCE", "SALES",
            "MARKETING_COMMUNICATIONS", "CUSTOMER_SERVICE", "HUMAN_RESOURCES", "ADMINISTRATION", "LEGAL",
            "DESIGN_CREATIVE", "EDUCATION_TRAINING", "HEALTHCARE_PHARMA", "ENGINEERING", "MANUFACTURING",
            "CONSTRUCTION_ARCHITECTURE", "REAL_ESTATE", "LOGISTICS_IMPORT_EXPORT", "HOSPITALITY_TOURISM",
            "RETAIL_CONSUMER", "AGRICULTURE", "MEDIA_PUBLISHING", "OTHER");

    @Autowired
    private MockMvc mockMvc;

    private static List<String> codesIn(String jsonArray) {
        List<String> codes = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"code\":\"([A-Z_]+)\"").matcher(jsonArray);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private static String arrayOf(String json, String field) {
        int start = json.indexOf("\"" + field + "\":[");
        int end = json.indexOf(']', start);
        return json.substring(start, end + 1);
    }

    @Test
    void anonymousGet_returns200_withBothCatalogsInSortOrder_andNoAliases() throws Exception {
        // Khong gui Authorization: /api/public/** mo qua quy uoc san co cua SecurityConfig.
        MvcResult result = mockMvc.perform(get("/api/public/catalogs")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(codesIn(arrayOf(json, "provinces"))).containsExactlyElementsOf(PROVINCE_ORDER);
        assertThat(codesIn(arrayOf(json, "industries"))).containsExactlyElementsOf(INDUSTRY_ORDER);
        assertThat(json).contains("{\"code\":\"HO_CHI_MINH\",\"label\":\"TP. Hồ Chí Minh\"}");
        // Bi danh (ten tinh cu, cach viet thuong gap) khong duoc lo ra.
        assertThat(json).doesNotContainIgnoringCase("alias").doesNotContain("Bình Dương").doesNotContain("Sài Gòn");
    }
}
