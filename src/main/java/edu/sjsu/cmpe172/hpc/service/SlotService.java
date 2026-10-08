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
    public PageDto<SlotDto> browse(Long provider, Long service, LocalDate date, Integer duration,
                                   boolean availableOnly, int page, int size) {
        return slots.browse(provider, service, date, duration, availableOnly, false, page, size, LocalDateTime.now(clock));
    }
}
