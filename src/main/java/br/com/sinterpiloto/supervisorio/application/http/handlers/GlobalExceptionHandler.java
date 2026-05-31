package br.com.sinterpiloto.supervisorio.application.http.handlers;

import br.com.sinterpiloto.supervisorio.domain.exceptions.PlcCommunicationException;
import br.com.sinterpiloto.supervisorio.domain.ports.out.PlcGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(PlcCommunicationException.class)
    public ResponseEntity<String> handlePlcError(PlcCommunicationException ex) {
        return ResponseEntity.internalServerError().body("Erro de comunicação com a PLC: " + ex.getMessage());
    }
}
