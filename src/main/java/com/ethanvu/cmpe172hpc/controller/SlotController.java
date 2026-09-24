package com.ethanvu.cmpe172hpc.controller;

import com.ethanvu.cmpe172hpc.dto.SlotDto;
import com.ethanvu.cmpe172hpc.service.SlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slots")
public class SlotController {
    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public List<SlotDto> availableSlots() {
        return slotService.getAvailableSlots();
    }
}
