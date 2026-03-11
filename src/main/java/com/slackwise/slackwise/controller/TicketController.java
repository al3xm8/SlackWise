package com.slackwise.slackwise.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.TenantConfig;
import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.security.TenantAccessService;
import com.slackwise.slackwise.service.AmazonService;
import com.slackwise.slackwise.service.ConnectwiseService;
import com.slackwise.slackwise.service.TicketStatsService;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    @Autowired
    private TicketStatsService ticketStatsService;

    @Autowired
    private ConnectwiseService connectwiseService;

    @Autowired
    private TenantAccessService tenantAccessService;

    @Autowired
    private AmazonService amazonService;

    @Value("${company.idnumber:19300}")
    private String fallbackTrackedCompanyId;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTicketStats() {
        String tenantId = resolveTenantId();
        List<String> trackedCompanyIds = resolveTrackedCompanyIds(tenantId);
        Map<String, Object> stats = ticketStatsService.getTicketStats(trackedCompanyIds);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/open")
    public ResponseEntity<List<Ticket>> getOpenTickets() throws IOException, InterruptedException {
        String tenantId = resolveTenantId();
        List<String> trackedCompanyIds = resolveTrackedCompanyIds(tenantId);
        List<Ticket> tickets = connectwiseService.fetchOpenTicketsByCompanyIds(trackedCompanyIds);
        return ResponseEntity.ok(tickets);
    }

    @PostMapping("/{ticketId}/responses")
    public ResponseEntity<Map<String, String>> addTicketResponse(
        @PathVariable String ticketId,
        @RequestBody AddResponseRequest request
    ) throws IOException, InterruptedException {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Response text is required"));
        }

        String tenantId = resolveTenantId();
        String companyScope = resolveCompanyScopeForTicket(tenantId, ticketId);

        Note note = new Note();
        note.setText(request.getText().trim());
        note.setDetailDescriptionFlag(true);
        note.setInternalAnalysisFlag(Boolean.TRUE.equals(request.getInternalAnalysisFlag()));
        note.setResolutionFlag(Boolean.TRUE.equals(request.getResolutionFlag()));

        connectwiseService.addNoteToTicket(companyScope, ticketId, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Response added"));
    }

    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<Map<String, String>> updateTicketStatus(
        @PathVariable String ticketId,
        @RequestBody UpdateStatusRequest request
    ) throws IOException, InterruptedException {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }

        String tenantId = resolveTenantId();
        String companyScope = resolveCompanyScopeForTicket(tenantId, ticketId);
        connectwiseService.updateTicketStatus(companyScope, ticketId, request.getStatus());
        return ResponseEntity.ok(Map.of("message", "Status updated"));
    }

    private String resolveTenantId() {
        return tenantAccessService.requiredTenantId();
    }

    private List<String> resolveTrackedCompanyIds(String tenantId) {
        TenantConfig config = amazonService.getTenantConfig(tenantId);
        Set<String> configuredCompanyIds = parseDelimitedValues(config != null ? config.getTrackedCompanyIds() : null);
        if (!configuredCompanyIds.isEmpty()) {
            return new ArrayList<>(configuredCompanyIds);
        }

        if (fallbackTrackedCompanyId != null && !fallbackTrackedCompanyId.isBlank()) {
            return List.of(fallbackTrackedCompanyId.trim());
        }

        return List.of("19300");
    }

    private String resolveCompanyScopeForTicket(String tenantId, String ticketId) throws IOException, InterruptedException {
        List<String> trackedCompanyIds = resolveTrackedCompanyIds(tenantId);
        if (trackedCompanyIds.size() == 1) {
            return trackedCompanyIds.get(0);
        }

        try {
            Ticket ticket = connectwiseService.fetchTicketById(trackedCompanyIds.get(0), ticketId);
            if (ticket != null && ticket.getCompany() != null) {
                if (ticket.getCompany().getId() > 0) {
                    String discoveredCompanyId = String.valueOf(ticket.getCompany().getId());
                    if (trackedCompanyIds.contains(discoveredCompanyId)) {
                        return discoveredCompanyId;
                    }
                }

                String discoveredIdentifier = ticket.getCompany().getIdentifier();
                if (discoveredIdentifier != null && !discoveredIdentifier.isBlank() && trackedCompanyIds.contains(discoveredIdentifier.trim())) {
                    return discoveredIdentifier.trim();
                }
            }
        } catch (IOException ex) {
            log.warn("Could not resolve explicit company for ticketId={} from tracked scope {}. Falling back to first tracked company.", ticketId, trackedCompanyIds, ex);
        }

        return trackedCompanyIds.get(0);
    }

    private Set<String> parseDelimitedValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        Set<String> values = new LinkedHashSet<>();
        String[] split = raw.split("[,;\\r\\n]+");
        for (String entry : split) {
            if (entry != null && !entry.isBlank()) {
                values.add(entry.trim());
            }
        }
        return values;
    }

    public static class AddResponseRequest {
        private String text;
        private Boolean internalAnalysisFlag;
        private Boolean resolutionFlag;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Boolean getInternalAnalysisFlag() {
            return internalAnalysisFlag;
        }

        public void setInternalAnalysisFlag(Boolean internalAnalysisFlag) {
            this.internalAnalysisFlag = internalAnalysisFlag;
        }

        public Boolean getResolutionFlag() {
            return resolutionFlag;
        }

        public void setResolutionFlag(Boolean resolutionFlag) {
            this.resolutionFlag = resolutionFlag;
        }
    }

    public static class UpdateStatusRequest {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
