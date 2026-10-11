package com.recruitment.ai.sync;

// FR-C07 K3 - ket qua cua mot lan goi AI dong bo thanh cong: record da parse + validate, va ten model (lay tu
// metadata cua ChatResponse, thieu thi dung model cau hinh - mau CvImprovementService.toResult).
public record SyncAiResult<T>(T entity, String model) {
}
