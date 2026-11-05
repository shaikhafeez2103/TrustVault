package com.trustvault.dto;

import java.time.LocalDateTime;

public class TamperCheckResult {
    private boolean valid;
    private int totalRecordsChecked;
    private Long brokenRecordId;
    private String details;
    private LocalDateTime checkedAt;

    public TamperCheckResult() {}

    public TamperCheckResult(boolean valid, int totalRecordsChecked, Long brokenRecordId, String details) {
        this.valid = valid;
        this.totalRecordsChecked = totalRecordsChecked;
        this.brokenRecordId = brokenRecordId;
        this.details = details;
        this.checkedAt = LocalDateTime.now();
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public int getTotalRecordsChecked() { return totalRecordsChecked; }
    public void setTotalRecordsChecked(int totalRecordsChecked) { this.totalRecordsChecked = totalRecordsChecked; }

    public Long getBrokenRecordId() { return brokenRecordId; }
    public void setBrokenRecordId(Long brokenRecordId) { this.brokenRecordId = brokenRecordId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
