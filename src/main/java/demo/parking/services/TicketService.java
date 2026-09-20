package demo.parking.services;

import demo.parking.Exceptions.VehicleHasNonExpiredTicketException;
import demo.parking.entities.Gate;
import demo.parking.entities.ParkingSpot;
import demo.parking.entities.Ticket;
import demo.parking.entities.Vehicle;
import demo.parking.enums.TicketStatus;
import demo.parking.Exceptions.TicketNotFoundException;
import demo.parking.repositories.TicketRepository;
import demo.parking.repositories.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service

public class TicketService {
    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);
    private final TicketRepository ticketRepository;
    private final PricingService pricingService;
    private final VehicleRepository vehicleRepository;

    public TicketService(TicketRepository ticketRepository, PricingService pricingService,
                         VehicleRepository vehicleRepository) {
        this.ticketRepository = ticketRepository;
        this.pricingService = pricingService;
        this.vehicleRepository = vehicleRepository;
    }


    private boolean hasNotExpiredTicket(Vehicle vehicle) {
        return !ticketRepository.findByVehicle_UuidAndStatusNot(vehicle.getUuid(), TicketStatus.EXPIRED).isEmpty();
    }

    @Transactional
    public Ticket generateTicket(Vehicle vehicle, ParkingSpot assignedSpot, Gate entryGate) {
        // Lock the stable parent row: locking tickets alone cannot protect an empty result.
        // The outer entry transaction retains this lock until admission commits or rolls back.
        vehicle = vehicleRepository.findByUuidForUpdate(vehicle.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle must be saved before ticket generation."));
        if (hasNotExpiredTicket(vehicle))
            throw new VehicleHasNonExpiredTicketException("Vehicle already has a non-expired ticket.");


        Ticket ticket = Ticket.builder()
                .assignedSpot(assignedSpot)
                .vehicle(vehicle)
                .entryGate(entryGate)
                .entryTime(LocalDateTime.now())
                .pricingPolicy(pricingService.getPricingPolicy())
                .pricingDescription(pricingService.getPricingDescription())
                .status(TicketStatus.ACTIVE)
                .build();
        Ticket savedTicket = ticketRepository.save(ticket);
        logger.info("Vehicle ticket generated and ticket: " + ticket);
        if(savedTicket == null) throw new RuntimeException("Ticket could not be saved");
        return savedTicket;
    }

    public Ticket findTicketByTicketId(UUID uuid){
        return ticketRepository.findById(uuid).orElseThrow(
                ()-> new TicketNotFoundException("Ticket could not be found with id: " + uuid)
        );
    }

    public Ticket findTicketByVehiclePlateNo(String plateNo, TicketStatus ticketStatus){
      List<Ticket> tickets = ticketRepository.findTicketsByVehicle_PlateNoAndStatus(plateNo, ticketStatus);
      if(tickets.isEmpty()) throw new TicketNotFoundException("Ticket could not be found for plateNo: " + plateNo+ " and status: " + ticketStatus);
      if(tickets.size() > 1) throw new RuntimeException("Too Many "+ ticketStatus+" Tickets in the SYSTEM for plate:" + plateNo);
      Ticket activeTicket = tickets.getFirst();
      logger.info("Vehicle ticket found: " + activeTicket);
      return activeTicket;
    }

    public void markAsParked(
            Ticket ticket,
            Vehicle vehicle,
            ParkingSpot actualSpot
    ) {
        ticket.setVehicle(vehicle);
        ticket.setActualSpot(actualSpot);
        ticket.setStatus(TicketStatus.PARKED);
        ticket.setActualParkedTime(LocalDateTime.now());
    }

    public void markAsAvailable(String vehiclePlate) {
        Ticket parkedTicket = findTicketByVehiclePlateNo(vehiclePlate, TicketStatus.PARKED);
        logger.info("Parked ticket found: " + parkedTicket+" and status: " + parkedTicket.getStatus());
        parkedTicket.setStatus(TicketStatus.ACTIVE);
        logger.info("Parked ticket status is currently: "+ parkedTicket.getStatus() +" and it is inside parking lot without spot");

    }

    public Ticket controlPenalty(Ticket ticket){
        ParkingSpot assignedSpot = ticket.getAssignedSpot();
        ParkingSpot actualSpot = ticket.getActualSpot();

        // A vehicle that left without ever parking has no actual spot to compare against,
        // so there is nothing to penalise here.
        if(assignedSpot == null || actualSpot == null){
            logger.warn("Penalty check skipped for ticket {}: assignedSpot present={}, actualSpot present={}",
                    ticket.getUuid(), assignedSpot != null, actualSpot != null);
            return ticket;
        }

        if(Objects.equals(assignedSpot.getAllowedType(), actualSpot.getAllowedType())){
            logger.info("Everything under control. No penalty to check");
        }
        else{
            ticket.setPenaltyApplied(true);
            findOutPenaltyReason(ticket);
        }
        return ticket;
    }

    public Ticket findOutPenaltyReason(Ticket ticket){
         ParkingSpot assignedSpot= ticket.getAssignedSpot();
         ParkingSpot actualSpot= ticket.getActualSpot();
         String reason = String.format("Penalty reason: AssignedSpot TYPE:%s  --- " +
                 "ActualSpot TYPE: %s",assignedSpot.getAllowedType(),actualSpot.getAllowedType());
            logger.warn("Penalty reason: " + reason);
         ticket.setPenaltyReason(reason);
         return ticket;
    }
}
