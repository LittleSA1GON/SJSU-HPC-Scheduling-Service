package edu.sjsu.cmpe172.hpc.service;

import edu.sjsu.cmpe172.hpc.dto.SlotDto;
import edu.sjsu.cmpe172.hpc.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlotService {
    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotDto> getAvailableSlots() {
        return slotRepository.findAvailableSlots();
    }
}
