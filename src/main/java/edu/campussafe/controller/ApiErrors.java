package edu.campussafe.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class) org.springframework.http.ResponseEntity<Map<String,String>> status(ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"Request failed")));}
  @ExceptionHandler(Exception.class) org.springframework.http.ResponseEntity<Map<String,String>> error(Exception e){return org.springframework.http.ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Request failed")));}
}
