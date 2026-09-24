package edu.sjsu.cmpe172.hpc.controller;

import edu.sjsu.cmpe172.hpc.dto.SlotDto;
import edu.sjsu.cmpe172.hpc.service.SlotService;
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
