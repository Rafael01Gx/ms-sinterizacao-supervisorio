package br.com.sinterpiloto.supervisorio.domain.exceptions;

public class PlcCommunicationException extends RuntimeException {
    public PlcCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}