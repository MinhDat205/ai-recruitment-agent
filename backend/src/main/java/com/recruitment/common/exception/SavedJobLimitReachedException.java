package com.recruitment.common.exception;

// FR-U16 R-L2 - them mot dong dang luu khi ung vien da co du tran (SavedJobService.MAX_ACTIVE_SAVED_JOBS).
public class SavedJobLimitReachedException extends RuntimeException {

    public SavedJobLimitReachedException(int maxActiveSavedJobs) {
        super("Bạn đã lưu tối đa " + maxActiveSavedJobs + " tin. Hãy bỏ lưu bớt để lưu tin mới.");
    }
}
