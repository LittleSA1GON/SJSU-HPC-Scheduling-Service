package com.ethanvu.cmpe172hpc.service;

import com.ethanvu.cmpe172hpc.dto.HomeDto;
import com.ethanvu.cmpe172hpc.repository.HomeRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    private final HomeRepository homeRepository;

    public HomeService(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
    }

    public HomeDto getHomeSummary() {
        return new HomeDto(
                "SJSU HPC Resource Reservation Portal",
                "HPC Resource Reservation and Job Scheduling",
                homeRepository.countProviders(),
                homeRepository.countServices(),
                homeRepository.countAvailableSlots()
        );
    }
}
