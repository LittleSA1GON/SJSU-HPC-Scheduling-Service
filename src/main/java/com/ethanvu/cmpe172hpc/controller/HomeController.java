package com.ethanvu.cmpe172hpc.controller;

import com.ethanvu.cmpe172hpc.dto.HomeDto;
import com.ethanvu.cmpe172hpc.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public HomeDto home() {
        return homeService.getHomeSummary();
    }
}
