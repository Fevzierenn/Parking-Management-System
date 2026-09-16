package demo.parking.services;

import demo.parking.Exceptions.ParkingSpotNotAvailableException;
import demo.parking.entities.*;
import demo.parking.enums.*;
import demo.parking.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the park-confirmation step. The guard that rejects an already occupied
 * spot was lost in merge 9b5d33d, which let a second vehicle overwrite the plate
 * of the car actually standing there; these tests pin it down.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParkingLotServiceIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ParkingLotService parkingLotService;
    @Autowired VehicleEntryService vehicleEntryService;
    @Autowired GateService gateService;

    @Autowired GateRepository gateRepository;
    @Autowired ParkingSpotRepository parkingSpotRepository;
    @Autowired ParkingFloorRepository parkingFloorRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired VehicleRepository vehicleRepository;

    private Long entryGateId;

    @BeforeEach
    void seed() {
        ticketRepository.deleteAll();
        parkingSpotRepository.deleteAll();
        parkingFloorRepository.deleteAll();
        gateRepository.deleteAll();
        vehicleRepository.deleteAll();

        ParkingFloor floor = new ParkingFloor();
        floor.setFloorNumber(0);
        ParkingFloor savedFloor = parkingFloorRepository.save(floor);

        for (int i = 1; i <= 3; i++) {
            parkingSpotRepository.save(carSpot(savedFloor, i));
        }

        Gate gate = new Gate();
        gate.setName("ENTRY-1");
        gate.setType(GateType.ENTRY);
        gate.setStatus(GateStatus.CLOSED);
        entryGateId = gateRepository.save(gate).getId();
    }

    private ParkingSpot carSpot(ParkingFloor floor, int sequence) {
        SpotDevice device = new SpotDevice();
        device.setDeviceStatus(DeviceStatus.EMPTY);
        device.setMessage("Ready");

        ParkingSpot spot = new ParkingSpot();
        spot.setFloor(floor);
        spot.setAllowedType(VehicleType.CAR);
        spot.setStatus(SpotStatus.AVAILABLE);
        spot.setNearness(sequence);
        spot.setSpotNumber("F0-CAR-00" + sequence);
        spot.setDevice(device);
        device.setSpot(spot);
        return spot;
    }

    /** Drives one vehicle through the entry gate and frees the barrier for the next one. */
    private Ticket enter(String plateNo) {
        Ticket ticket = vehicleEntryService.parkVehicle(plateNo, VehicleType.CAR, entryGateId);
        gateService.vehiclePassed(entryGateId);
        return ticket;
    }

    private ParkingSpot reload(Long spotId) {
        return parkingSpotRepository.findById(spotId).orElseThrow();
    }

    private Long deviceIdOf(Long spotId) {
        return reload(spotId).getDevice().getId();
    }

    private ParkingSpot spotByNumber(String spotNumber) {
        return parkingSpotRepository.findAll().stream()
                .filter(s -> s.getSpotNumber().equals(spotNumber))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void vehicleParkingOnItsOwnSpotIsAccepted() {
        Ticket ticket = enter("34 AAA 111");
        Long spotId = ticket.getAssignedSpot().getId();

        parkingLotService.vehicleReachTheSpot("34 AAA 111", deviceIdOf(spotId));

        ParkingSpot spot = reload(spotId);
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.OCCUPIED);
        assertThat(spot.getDevice().getDeviceStatus()).isEqualTo(DeviceStatus.OCCUPIED);
        assertThat(spot.getDevice().getVehiclePlate()).isEqualTo("34 AAA 111");

        Ticket stored = ticketRepository.findById(ticket.getUuid()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.PARKED);
        assertThat(stored.getActualSpot().getId()).isEqualTo(spotId);
    }

    @Test
    void secondVehicleCannotTakeAnAlreadyOccupiedSpot() {
        Ticket first = enter("34 AAA 111");
        Long takenSpotId = first.getAssignedSpot().getId();
        parkingLotService.vehicleReachTheSpot("34 AAA 111", deviceIdOf(takenSpotId));

        Ticket second = enter("34 BBB 222");
        assertThat(second.getAssignedSpot().getId())
                .as("second vehicle must be allocated a different spot")
                .isNotEqualTo(takenSpotId);

        // The second vehicle physically pulls into the spot the first one is standing in.
        assertThatThrownBy(() ->
                parkingLotService.vehicleReachTheSpot("34 BBB 222", deviceIdOf(takenSpotId)))
                .isInstanceOf(ParkingSpotNotAvailableException.class);

        // The first vehicle must be untouched: this is the overwrite the guard prevents.
        ParkingSpot taken = reload(takenSpotId);
        assertThat(taken.getStatus()).isEqualTo(SpotStatus.OCCUPIED);
        assertThat(taken.getDevice().getVehiclePlate()).isEqualTo("34 AAA 111");

        assertThat(ticketRepository.findById(first.getUuid()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.PARKED);
        assertThat(ticketRepository.findById(second.getUuid()).orElseThrow().getStatus())
                .as("rejected vehicle keeps its ACTIVE ticket")
                .isEqualTo(TicketStatus.ACTIVE);
    }

    @Test
    void parkingOnADifferentButFreeSpotStillReleasesTheAssignedOne() {
        Ticket ticket = enter("34 AAA 111");
        Long assignedSpotId = ticket.getAssignedSpot().getId();

        ParkingSpot freeSpot = spotByNumber("F0-CAR-003");
        assertThat(freeSpot.getId()).isNotEqualTo(assignedSpotId);

        parkingLotService.vehicleReachTheSpot("34 AAA 111", deviceIdOf(freeSpot.getId()));

        assertThat(reload(assignedSpotId).getStatus())
                .as("the abandoned reservation goes back to the pool")
                .isEqualTo(SpotStatus.AVAILABLE);
        assertThat(reload(freeSpot.getId()).getStatus()).isEqualTo(SpotStatus.OCCUPIED);

        Ticket stored = ticketRepository.findById(ticket.getUuid()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.PARKED);
        assertThat(stored.getActualSpot().getId()).isEqualTo(freeSpot.getId());
    }

    @Test
    void theRejectionSurfacesAsConflictOverHttp() throws Exception {
        Ticket first = enter("34 AAA 111");
        Long takenSpotId = first.getAssignedSpot().getId();
        parkingLotService.vehicleReachTheSpot("34 AAA 111", deviceIdOf(takenSpotId));

        enter("34 BBB 222");

        mockMvc.perform(post("/api/v1/spot-devices/{deviceId}/vehicle-detection", deviceIdOf(takenSpotId))
                        .param("plateNo", "34 BBB 222"))
                .andExpect(status().isConflict());
    }
}
