package org.example.backend.service;

import java.util.Map;

public interface DashboardService {
    Map<String, Object> getOverview(Integer managerUserId);
}
