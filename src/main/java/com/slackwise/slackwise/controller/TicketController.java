package com.slackwise.slackwise.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.service.ConnectwiseService;
import com.slackwise.slackwise.service.TicketStatsService;



@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketStatsService ticketStatsService;

    @Autowired
    private ConnectwiseService connectwiseService;

    @Value("${company.idnumber}")
    private String tenantId;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTicketStats() {
        Map<String, Object> stats = ticketStatsService.getTicketStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/open")
    public ResponseEntity<List<Ticket>> getOpenTickets() throws IOException, InterruptedException {
        List<Ticket> tickets = connectwiseService.fetchOpenTicketsByCompanyId(tenantId);
        return ResponseEntity.ok(tickets);
    }

    @PostMapping("/{ticketId}/responses")
    public ResponseEntity<Map<String, String>> addTicketResponse(@PathVariable String ticketId, @RequestBody AddResponseRequest request)
        throws IOException, InterruptedException {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Response text is required"));
        }

        Note note = new Note();
        note.setText(request.getText().trim());
        note.setDetailDescriptionFlag(true);
        note.setInternalAnalysisFlag(Boolean.TRUE.equals(request.getInternalAnalysisFlag()));
        note.setResolutionFlag(Boolean.TRUE.equals(request.getResolutionFlag()));

        connectwiseService.addNoteToTicket(tenantId, ticketId, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Response added"));
    }

    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<Map<String, String>> updateTicketStatus(@PathVariable String ticketId, @RequestBody UpdateStatusRequest request)
        throws IOException, InterruptedException {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }

        connectwiseService.updateTicketStatus(tenantId, ticketId, request.getStatus());
        return ResponseEntity.ok(Map.of("message", "Status updated"));
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
