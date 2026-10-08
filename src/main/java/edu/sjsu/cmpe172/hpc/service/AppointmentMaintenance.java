package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.repository.AppointmentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class AppointmentMaintenance {
    private final AppointmentRepository appointments;
    private final Clock clock;
    public AppointmentMaintenance(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments; this.clock = clock;
    }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void completePastReservations() { appointments.completePast(LocalDateTime.now(clock)); }
}
