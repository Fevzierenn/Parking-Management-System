package demo.parking.services;

import demo.parking.Exceptions.VehicleHasNonExpiredTicketException;
import demo.parking.entities.*;
import demo.parking.enums.*;
import demo.parking.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class TicketAdmissionConcurrencyTest {
    @Autowired TicketService ticketService;
    @Autowired VehicleEntryService entryService;
    @Autowired VehicleRepository vehicles;
    @Autowired TicketRepository tickets;
    @Autowired GateRepository gates;
    @Autowired ParkingFloorRepository floors;
    @Autowired ParkingSpotRepository spots;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void ticketGenerationWaitsForTheOtherAdmissionToCommit() throws Exception {
        Vehicle vehicle = vehicle();
        Gate firstGate = gate();
        Gate secondGate = gate();
        ParkingSpot firstSpot = spot();
        ParkingSpot secondSpot = spot();
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CountDownLatch competing = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Ticket> first = executor.submit(() -> transaction().execute(status -> {
                Ticket ticket = ticketService.generateTicket(vehicle, firstSpot, firstGate);
                tickets.flush();
                created.countDown();
                await(commit);
                return ticket;
            }));
            await(created);

            Future<Ticket> second = executor.submit(() -> transaction().execute(status -> {
                // Establish a read before the competing admission commits.
                vehicles.findByPlateNo(vehicle.getPlateNo()).orElseThrow();
                competing.countDown();
                return ticketService.generateTicket(vehicle, secondSpot, secondGate);
            }));
            await(competing);
            try {
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                commit.countDown();
            }

            assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull();
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(VehicleHasNonExpiredTicketException.class);
            assertThat(tickets.findTicketsByVehicle_PlateNoAndStatus(vehicle.getPlateNo(), TicketStatus.ACTIVE))
                    .hasSize(1);
        } finally {
            commit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentEntryAtDifferentGatesIssuesOneTicketAndRollsBackTheLoser() throws Exception {
        Vehicle vehicle = vehicle();
        Gate firstGate = gate();
        Gate secondGate = gate();
        spot();
        spot();
        long reservedBefore = spots.findAll().stream().filter(s -> s.getStatus() == SpotStatus.RESERVED).count();
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> enter(vehicle, firstGate, start));
            Future<Boolean> second = executor.submit(() -> enter(vehicle, secondGate, start));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(tickets.findTicketsByVehicle_PlateNoAndStatus(vehicle.getPlateNo(), TicketStatus.ACTIVE))
                    .hasSize(1);
            assertThat(List.of(gates.findById(firstGate.getId()).orElseThrow().getStatus(),
                    gates.findById(secondGate.getId()).orElseThrow().getStatus()))
                    .containsExactlyInAnyOrder(GateStatus.OPEN, GateStatus.CLOSED);
            assertThat(spots.findAll().stream().filter(s -> s.getStatus() == SpotStatus.RESERVED).count())
                    .isEqualTo(reservedBefore + 1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"ACTIVE", "PARKED", "PAYMENT_PENDING"})
    void everyNonExpiredStatusBlocksAnotherTicket(TicketStatus status) {
        Vehicle vehicle = vehicle();
        Ticket existing = ticketService.generateTicket(vehicle, spot(), gate());
        existing.setStatus(status);
        tickets.saveAndFlush(existing);

        assertThatThrownBy(() -> ticketService.generateTicket(vehicle, spot(), gate()))
                .isInstanceOf(VehicleHasNonExpiredTicketException.class);
    }

    @Test
    void expiredTicketsAllowAnotherAdmission() {
        Vehicle vehicle = vehicle();
        Ticket previous = ticketService.generateTicket(vehicle, spot(), gate());
        previous.setStatus(TicketStatus.EXPIRED);
        tickets.saveAndFlush(previous);

        Ticket next = ticketService.generateTicket(vehicle, spot(), gate());

        assertThat(next.getUuid()).isNotEqualTo(previous.getUuid());
        assertThat(next.getStatus()).isEqualTo(TicketStatus.ACTIVE);
    }

    private boolean enter(Vehicle vehicle, Gate gate, CyclicBarrier start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            entryService.parkVehicle(vehicle.getPlateNo(), vehicle.getType(), gate.getId());
            return true;
        } catch (VehicleHasNonExpiredTicketException expected) {
            return false;
        }
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private Vehicle vehicle() {
        return vehicles.saveAndFlush(Vehicle.builder()
                .plateNo("concurrent-" + UUID.randomUUID()).type(VehicleType.CAR).build());
    }

    private Gate gate() {
        Gate gate = new Gate();
        gate.setName("CONCURRENT-" + UUID.randomUUID());
        gate.setType(GateType.ENTRY);
        gate.setStatus(GateStatus.CLOSED);
        return gates.saveAndFlush(gate);
    }

    private ParkingSpot spot() {
        ParkingFloor floor = new ParkingFloor();
        floor.setFloorNumber(0);
        floor = floors.saveAndFlush(floor);
        ParkingSpot spot = new ParkingSpot();
        spot.setFloor(floor);
        spot.setSpotNumber("CONCURRENT-" + UUID.randomUUID());
        spot.setAllowedType(VehicleType.CAR);
        spot.setStatus(SpotStatus.AVAILABLE);
        SpotDevice device = new SpotDevice();
        device.setDeviceStatus(DeviceStatus.EMPTY);
        device.setSpot(spot);
        spot.setDevice(device);
        return spots.saveAndFlush(spot);
    }
}
