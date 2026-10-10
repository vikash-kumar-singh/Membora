package com.membora.platform.auth;

import java.util.Set;

public final class Permissions {
    private Permissions() {}
    public static final String ORG_MANAGE = "ORG_MANAGE";
    public static final String STAFF_MANAGE = "STAFF_MANAGE";
    public static final String MEMBER_READ = "MEMBER_READ";
    public static final String MEMBER_WRITE = "MEMBER_WRITE";
    public static final String MEMBERSHIP_WRITE = "MEMBERSHIP_WRITE";
    public static final String PAYMENT_WRITE = "PAYMENT_WRITE";
    public static final String ATTENDANCE_WRITE = "ATTENDANCE_WRITE";
    public static final String REPORT_READ = "REPORT_READ";
    public static final String AUTOMATION_MANAGE = "AUTOMATION_MANAGE";
    public static final String SENSITIVE_READ = "SENSITIVE_READ";
    public static final Set<String> ALL = Set.of(ORG_MANAGE, STAFF_MANAGE, MEMBER_READ, MEMBER_WRITE, MEMBERSHIP_WRITE, PAYMENT_WRITE, ATTENDANCE_WRITE, REPORT_READ, AUTOMATION_MANAGE, SENSITIVE_READ);
}
