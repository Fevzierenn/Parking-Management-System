package demo.parking.DTO.RequestDTO;

import demo.parking.entities.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleExitRequest(
        @NotBlank(message = "PlateNo is Required")
        String plateNo,

        @NotNull(message = "Gate Id is Required")
        Long gateId
) {
}
