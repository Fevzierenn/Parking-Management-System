package demo.parking.Exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(value = TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTicketNotFoundException(TicketNotFoundException ex, WebRequest request) {
        log.warn("TicketNotFound: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(value = VehicleHasNonExpiredTicketException.class)
    public ResponseEntity<ErrorResponse> handleVehicleHasNonExpiredTicketException(VehicleHasNonExpiredTicketException ex, WebRequest request) {
        log.warn("VehicleHasNonExpiredTicket: {}", ex.getMessage());
        log.debug("VehicleHasNonExpiredTicketException WebRequest: {}", request);
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }



    @ExceptionHandler(value = GateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGateNotFoundException(GateNotFoundException ex, WebRequest request) {
        log.warn("GateNotFound: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(value = InvalidGateTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGateTypeException(InvalidGateTypeException ex, WebRequest request) {
        log.warn("InvalidGateType: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(value = ParkingSpotNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleParkingSpotNotAvailableException(ParkingSpotNotAvailableException ex, WebRequest request) {
        log.warn("ParkingSpotNotAvailable: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}
