package com.example.azuredevops.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health")
class HealthController {

    @GetMapping("/")
    fun health() = mapOf(
        "status" to "ok",
        "message" to "Azure DevOps PR API is running"
    )
}

