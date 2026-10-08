package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProviderTransactionService {
    private final SlotRepository slots;
    private final CatalogRepository catalog;
    private final Clock clock;
    public ProviderTransactionService(SlotRepository slots, CatalogRepository catalog, Clock clock) {
        this.slots = slots; this.catalog = catalog; this.clock = clock;
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SlotDto create(long userId, SlotRequest request) {
        long providerId = catalog.providerIdForUser(userId);
        catalog.service(request.serviceId());
        if (!request.startTime().isAfter(LocalDateTime.now(clock))) throw ApiException.badRequest("Start time must be in the future");
        long minutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (minutes < 1 || minutes > 1440 || request.startTime().getSecond() != 0 ||
            request.endTime().getSecond() != 0 || request.startTime().getNano() != 0 || request.endTime().getNano() != 0)
            throw ApiException.badRequest("Use whole minutes and a duration between 1 minute and 24 hours");
        return slots.create(providerId, request).orElseThrow(() -> ApiException.conflict("An overlapping slot already exists for this service"));
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void remove(long userId, long slotId, long version) {
        long providerId = catalog.providerIdForUser(userId);
        SlotDto slot = slots.findById(slotId).orElseThrow(() -> ApiException.notFound("Slot not found"));
        if (slot.providerId() != providerId) throw ApiException.forbidden("Only this slot's provider can remove it");
        if ("CLOSED".equals(slot.status())) return;
        if (slots.close(slotId, providerId, version) != 1)
            throw ApiException.conflict("A booked or changed slot cannot be removed. Refresh availability.");
    }
}
