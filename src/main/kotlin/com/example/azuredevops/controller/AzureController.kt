package com.example.azuredevops.controller

import com.example.azuredevops.service.AzureDevOpsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/azure")
@Tag(name = "Azure DevOps")
class AzureController(private val service: AzureDevOpsService) {

    @GetMapping("/repositories")
    @Operation(summary = "List all Git repositories in the project")
    fun listRepositories(): List<Any> = service.getRepositories()

    @GetMapping("/pull-requests")
    @Operation(summary = "List pull requests (optionally filtered by repo, status, count)")
    fun listPullRequests(
        @RequestParam(required = false) repositoryId: String?,
        @RequestParam(defaultValue = "active") status: String,
        @RequestParam(defaultValue = "10") top: Int
    ): List<Any> = service.getPullRequests(repositoryId, status, top)
}

