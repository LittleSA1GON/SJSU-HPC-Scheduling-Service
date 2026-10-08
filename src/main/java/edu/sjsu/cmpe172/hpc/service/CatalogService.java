package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.CatalogDto;
import edu.sjsu.cmpe172.hpc.repository.CatalogRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
@Service
public class CatalogService {
    private final CatalogRepository catalog;
    private final Clock clock;
    public CatalogService(CatalogRepository catalog, Clock clock) { this.catalog = catalog; this.clock = clock; }
    public CatalogDto getCatalog() {
        return new CatalogDto(catalog.services(), catalog.providers(), clock.getZone().getId(), LocalDate.now(clock).toString());
    }
}
