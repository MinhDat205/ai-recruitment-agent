package com.recruitment.messagedraft.dto;

import com.recruitment.ai.messagedraft.DraftScenario;
import com.recruitment.messagedraft.DraftUnavailableReason;
import java.util.List;

// FR-C07 A1 (muc 4.4) - thu tu co dinh theo bang R-S1 cua phia goi. KHONG them field nao khac (T11).
public record DraftScenariosResponse(List<ScenarioOption> scenarios) {

    // unavailableReason null khi available.
    public record ScenarioOption(DraftScenario scenario, boolean available, DraftUnavailableReason unavailableReason) {
    }
}
