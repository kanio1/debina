package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.service.PaymentService.PaymentTimelinePage;
import com.sepanexus.modules.paymentlifecycle.service.PaymentTimelineLookup.TimelineEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentTimelineResponse(List<TimelineEntryResponse> items, Integer nextAfterSeq) {

    static PaymentTimelineResponse from(PaymentTimelinePage page) {
        return new PaymentTimelineResponse(
                page.items().stream().map(TimelineEntryResponse::from).toList(),
                page.nextAfterSeq());
    }

    public record TimelineEntryResponse(int seq, String fromStatus, String toStatus, String statusCode,
            String reasonCode, String sourceType, String actorType, boolean isFinal, String eventType,
            UUID eventRef, Instant at) {
        static TimelineEntryResponse from(TimelineEntry entry) {
            return new TimelineEntryResponse(entry.seq(), entry.fromStatus(), entry.toStatus(), entry.statusCode(),
                    entry.reasonCode(), entry.sourceType(), entry.actorType(), entry.isFinal(), entry.eventType(),
                    entry.eventRef(), entry.at());
        }
    }
}
