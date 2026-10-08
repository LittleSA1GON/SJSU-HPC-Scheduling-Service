package edu.sjsu.cmpe172.hpc.controller;
import edu.sjsu.cmpe172.hpc.dto.CatalogDto;
import edu.sjsu.cmpe172.hpc.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }
    @GetMapping("/api/catalog")
    public CatalogDto catalog() { return catalog.getCatalog(); }
}
