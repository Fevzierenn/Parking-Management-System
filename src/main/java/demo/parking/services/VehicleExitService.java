package demo.parking.services;

import demo.parking.config.PricingPropertyConfig;
import demo.parking.entities.Gate;
import demo.parking.entities.Ticket;
import demo.parking.entities.Vehicle;
import demo.parking.enums.TicketStatus;
import demo.parking.strategy.pricing.PricingStrategy;
import demo.parking.strategy.pricing.PricingStrategyService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class VehicleExitService {

    private static final Logger log = LoggerFactory.getLogger(VehicleExitService.class);
    private final TicketService ticketService;
    private final GateService gateService;
    private final VehicleService vehicleService;
    private final PricingService pricingService;

    public VehicleExitService(TicketService ticketService, GateService gateService, VehicleService vehicleService, PricingStrategyService pricingStrategyService, PricingService pricingService) {
        this.ticketService = ticketService;
        this.gateService = gateService;
        this.vehicleService = vehicleService;
        this.pricingService = pricingService;
    }

    @Transactional
    public Ticket vehicleExit(@NotEmpty String plateNo, @NotNull Long exitGateId) {
        log.info("Vehicle exit for plateNo: {} and Exit Gate Id: {}",plateNo, exitGateId);
        Ticket exitVehicleTicket = ticketService.findTicketByVehiclePlateNo(plateNo, TicketStatus.ACTIVE);
        log.info("Exit Vehicle Ticket: {}", exitVehicleTicket);
        Gate exitGate = gateService.findExitGateByIdForUpdate(exitGateId);

        ticketService.controlPenalty(exitVehicleTicket);
        exitVehicleTicket.setExitTime(LocalDateTime.now());
        pricingService.calculate(exitVehicleTicket);
        exitVehicleTicket.setStatus(TicketStatus.EXPIRED);

        gateService.openForExit(exitGate);   // opens last: a failed allocation leaves the barrier down
        return exitVehicleTicket;


    }
}
