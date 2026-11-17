package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.PageDto;
import edu.sjsu.cmpe172.hpc.dto.SlotDto;
import edu.sjsu.cmpe172.hpc.repository.SlotRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
@Service
public class SlotService {
    private final SlotRepository slots;
    private final Clock clock;
    public SlotService(SlotRepository slots, Clock clock) { this.slots = slots; this.clock = clock; }
    public SlotDto available(long id) {
        var slot = slots.findById(id).orElseThrow(() -> edu.sjsu.cmpe172.hpc.exception.ApiException.notFound("Slot not found"));
        if (!"OPEN".equals(slot.status()) || !LocalDateTime.parse(slot.startTime()).isAfter(LocalDateTime.now(clock)))
            throw edu.sjsu.cmpe172.hpc.exception.ApiException.conflict("That slot is no longer available. Search for alternatives.");
        return slot;
    }
    public PageDto<SlotDto> browse(Long provider, Long service, LocalDate date, Integer duration,
                                   boolean availableOnly, int page, int size) {
        return slots.browse(provider, service, date, duration, availableOnly, false, page, size, LocalDateTime.now(clock));
    }
}
