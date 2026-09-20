package demo.parking.repositories;

import demo.parking.entities.Ticket;
import demo.parking.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Ticket findTicketsByVehicle_Uuid(UUID vehicleUuid);
    
    List<Ticket> findTicketsByVehicle_PlateNoAndStatus(String plateNo, TicketStatus status);

    boolean existsByVehicle_UuidAndStatusNot(UUID uuid, TicketStatus ticketStatus);

    // A locking read observes current committed rows under MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Ticket> findByVehicle_UuidAndStatusNot(UUID uuid, TicketStatus ticketStatus);

}
