package com.example.azuredevops.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(HttpClientErrorException::class)
    fun handleClientError(ex: HttpClientErrorException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(ex.statusCode)
            .body(mapOf("error" to (ex.message ?: "Client error"), "status" to ex.statusCode.value()))

    @ExceptionHandler(HttpServerErrorException::class)
    fun handleServerError(ex: HttpServerErrorException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(ex.statusCode)
            .body(mapOf("error" to (ex.message ?: "Server error"), "status" to ex.statusCode.value()))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Map<String, Any>> =
        ResponseEntity.internalServerError()
            .body(mapOf("error" to (ex.message ?: "Unexpected error")))
}

