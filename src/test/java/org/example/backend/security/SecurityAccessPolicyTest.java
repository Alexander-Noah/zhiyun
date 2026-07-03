package org.example.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAccessPolicyTest {
    private final Object policy = newPolicy();

    @Test
    void userAdministrationRequiresSystemAdmin() {
        assertTrue(isAllowed("GET", "/users", "systemAdmin"));
        assertTrue(isAllowed("POST", "/admin/users/1/reset-password", "systemAdmin"));
        assertTrue(isAllowed("GET", "/users/options", "labAdmin"));

        assertFalse(isAllowed("GET", "/users", "teacher"));
        assertFalse(isAllowed("GET", "/users/options", "teacher"));
        assertFalse(isAllowed("POST", "/admin/users/1/reset-password", "labAdmin"));
        assertFalse(isAllowed("POST", "/users/options", "labAdmin"));
        assertFalse(isAllowed("POST", "/users/batch/role", "maintenance"));
    }

    @Test
    void systemSettingsAndActivationManagementRequireSystemAdmin() {
        assertTrue(isAllowed("PUT", "/system-settings", "systemAdmin"));
        assertTrue(isAllowed("POST", "/activation-codes/generate", "systemAdmin"));

        assertFalse(isAllowed("PUT", "/system-settings", "labAdmin"));
        assertFalse(isAllowed("GET", "/admin/activation-codes", "labAdmin"));
        assertFalse(isAllowed("POST", "/activation-codes/generate", "labAdmin"));
        assertFalse(isAllowed("POST", "/activation-codes/batch", "labAdmin"));
        assertFalse(isAllowed("POST", "/activation-codes/generate", "teacher"));
    }

    @Test
    void labAdminCanUseOnlyLabActivationBindingEndpoints() {
        assertTrue(isAllowed("GET", "/activation-codes/lab-bindings", "labAdmin"));
        assertTrue(isAllowed("POST", "/activation-codes/bind-lab", "labAdmin"));
        assertTrue(isAllowed("POST", "/activation-codes/abc/unbind-lab", "labAdmin"));

        assertFalse(isAllowed("GET", "/activation-codes/lab-bindings", "teacher"));
        assertFalse(isAllowed("POST", "/activation-codes/bind-lab", "teacher"));
        assertFalse(isAllowed("POST", "/activation-codes/abc/unbind-lab", "teacher"));
    }

    @Test
    void labAdminCanManageLabResourcesButTeacherCannotMutateThem() {
        assertTrue(isAllowed("POST", "/devices/import", "labAdmin"));
        assertTrue(isAllowed("PUT", "/labs/12", "labAdmin"));
        assertTrue(isAllowed("POST", "/consumables/12/movement", "labAdmin"));
        assertTrue(isAllowed("POST", "/class-timetables/crawl", "labAdmin"));
        assertTrue(isAllowed("POST", "/lab-software", "labAdmin"));
        assertTrue(isAllowed("PUT", "/environment-templates/3", "labAdmin"));
        assertTrue(isAllowed("GET", "/iot/hardware", "labAdmin"));
        assertTrue(isAllowed("POST", "/iot/labs/12/access", "labAdmin"));
        assertTrue(isAllowed("POST", "/reservations/9/approve", "labAdmin"));
        assertTrue(isAllowed("POST", "/schedule-adjustments/9/approve", "labAdmin"));
        assertTrue(isAllowed("POST", "/course-environment-requests/9/confirm", "labAdmin"));
        assertTrue(isAllowed("POST", "/repairs/9/assign", "labAdmin"));
        assertTrue(isAllowed("POST", "/notices", "labAdmin"));
        assertTrue(isAllowed("POST", "/usage-records/9/review", "labAdmin"));
        assertTrue(isAllowed("GET", "/business-loop/overview", "labAdmin"));

        assertFalse(isAllowed("POST", "/devices/import", "teacher"));
        assertFalse(isAllowed("PUT", "/labs/12", "teacher"));
        assertFalse(isAllowed("POST", "/consumables/12/movement", "teacher"));
        assertFalse(isAllowed("POST", "/iot/labs/12/access", "teacher"));
        assertFalse(isAllowed("POST", "/reservations/9/approve", "teacher"));
        assertFalse(isAllowed("POST", "/schedule-adjustments/9/approve", "teacher"));
        assertFalse(isAllowed("POST", "/course-environment-requests/9/confirm", "teacher"));
    }

    @Test
    void teacherAndMaintenanceRolesKeepOnlyTheirExpectedSurfaces() {
        assertTrue(isAllowed("GET", "/class-timetables", "teacher"));
        assertTrue(isAllowed("POST", "/course-environment-requests", "teacher"));
        assertFalse(isAllowed("POST", "/class-timetables/crawl", "teacher"));

        assertTrue(isAllowed("GET", "/repairs", "maintenance"));
        assertTrue(isAllowed("POST", "/repairs/RO-2026/complete", "maintenance"));
        assertFalse(isAllowed("DELETE", "/users/1", "maintenance"));
    }

    private Object newPolicy() {
        try {
            return Class.forName("org.example.backend.security.SecurityAccessPolicy")
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean isAllowed(String method, String path, String roleCode) {
        try {
            return (Boolean) policy.getClass()
                    .getMethod("isAllowed", String.class, String.class, String.class)
                    .invoke(policy, method, path, roleCode);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
