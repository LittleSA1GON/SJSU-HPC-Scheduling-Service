package edu.sjsu.cmpe172.hpc.service;

import edu.sjsu.cmpe172.hpc.dto.HomeDto;
import edu.sjsu.cmpe172.hpc.repository.HomeRepository;
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
