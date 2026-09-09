package demo.parking.Controller;

import demo.parking.DTO.RequestDTO.VehicleExitRequest;
import demo.parking.entities.Invoice;
import demo.parking.services.VehicleExitService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicle-exit")
public class VehicleExitController {
    private static final Logger log = LoggerFactory.getLogger(VehicleExitController.class);
    private final VehicleExitService vehicleExitService;

    public VehicleExitController(VehicleExitService vehicleExitService) {
        this.vehicleExitService = vehicleExitService;
    }

    @PostMapping
    public Invoice exitVehicle(@Valid @RequestBody VehicleExitRequest request) {
        log.info("Exit Vehicle Request: {}", request);
        vehicleExitService.vehicleExit(request.plateNo(), request.gateId());

        return new Invoice();
    }
}
