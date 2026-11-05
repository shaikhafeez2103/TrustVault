package com.trustvault.dto;

public class SecurityStatsResponse {
    private long totalDocuments;
    private long totalAuditLogs;
    private long failedLogins24h;
    private long unauthorizedAccessAttempts24h;
    private long activePermissions;
    private boolean tamperDetected;
    private boolean suspiciousActivityFlag;

    public SecurityStatsResponse() {}

    public long getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }

    public long getTotalAuditLogs() { return totalAuditLogs; }
    public void setTotalAuditLogs(long totalAuditLogs) { this.totalAuditLogs = totalAuditLogs; }

    public long getFailedLogins24h() { return failedLogins24h; }
    public void setFailedLogins24h(long failedLogins24h) { this.failedLogins24h = failedLogins24h; }

    public long getUnauthorizedAccessAttempts24h() { return unauthorizedAccessAttempts24h; }
    public void setUnauthorizedAccessAttempts24h(long unauthorizedAccessAttempts24h) { this.unauthorizedAccessAttempts24h = unauthorizedAccessAttempts24h; }

    public long getActivePermissions() { return activePermissions; }
    public void setActivePermissions(long activePermissions) { this.activePermissions = activePermissions; }

    public boolean isTamperDetected() { return tamperDetected; }
    public void setTamperDetected(boolean tamperDetected) { this.tamperDetected = tamperDetected; }

    public boolean isSuspiciousActivityFlag() { return suspiciousActivityFlag; }
    public void setSuspiciousActivityFlag(boolean suspiciousActivityFlag) { this.suspiciousActivityFlag = suspiciousActivityFlag; }
}
