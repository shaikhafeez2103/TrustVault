package com.trustvault.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecentController {

    /**
     * Dummy recent activity list – in a real app this would query the DB.
     */
    @GetMapping("/recent-activities")
    public ResponseEntity<List<Map<String, String>>> getRecentActivities() {
        List<Map<String, String>> activities = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a");
        // Example data
        activities.add(create("User 'Alice' uploaded 'Report.pdf'", LocalDateTime.now().minusHours(1).format(fmt)));
        activities.add(create("User 'Bob' accessed 'Policy.docx'", LocalDateTime.now().minusHours(3).format(fmt)));
        activities.add(create("User 'Admin' changed settings", LocalDateTime.now().minusDays(1).format(fmt)));
        return ResponseEntity.ok(activities);
    }

    private Map<String, String> create(String description, String timestamp) {
        Map<String, String> map = new HashMap<>();
        map.put("description", description);
        map.put("timestamp", timestamp);
        return map;
    }

    /**
     * Dummy pending access requests.
     */
    @GetMapping("/pending-requests")
    public ResponseEntity<List<Map<String, String>>> getPendingRequests() {
        List<Map<String, String>> requests = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a");
        requests.add(createReq("Alice Jenkins", "Financials.pdf", LocalDateTime.now().minusHours(2).format(fmt)));
        requests.add(createReq("Bob Smith", "ProjectPlan.docx", LocalDateTime.now().minusHours(5).format(fmt)));
        return ResponseEntity.ok(requests);
    }

    private Map<String, String> createReq(String user, String document, String requestedAt) {
        Map<String, String> map = new HashMap<>();
        map.put("user", user);
        map.put("document", document);
        map.put("requestedAt", requestedAt);
        return map;
    }
}
