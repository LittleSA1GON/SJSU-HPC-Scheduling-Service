package com.ethanvu.cmpe172hpc.service;

import com.ethanvu.cmpe172hpc.dto.SlotDto;
import com.ethanvu.cmpe172hpc.repository.SlotRepository;
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
