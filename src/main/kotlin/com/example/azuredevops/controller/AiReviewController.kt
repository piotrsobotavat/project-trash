package com.example.azuredevops.controller

import com.example.azuredevops.service.AiReviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ai")
@Tag(name = "PR-Review-Accelerator Agent")
class AiReviewController(private val aiReviewService: AiReviewService) {

    @GetMapping("/hello")
    @Operation(summary = "Say hello to the PR-Review-Accelerator agent")
    fun hello(): Map<String, String> {
        val response = aiReviewService.sayHello()
        return mapOf("agent" to "PR-Review-Accelerator", "response" to response)
    }

    @PostMapping("/chat")
    @Operation(summary = "Send a custom message to the PR-Review-Accelerator agent")
    fun chat(@RequestBody body: Map<String, String>): Map<String, String> {
        val message = body["message"] ?: error("'message' field is required")
        val response = aiReviewService.chat(message)
        return mapOf("agent" to "PR-Review-Accelerator", "message" to message, "response" to response)
    }

    @GetMapping("/review/pull-requests/{id}")
    @Operation(summary = "Ask PR-Review-Accelerator to review a pull request by its ID")
    fun reviewPullRequest(
        @PathVariable id: Int,
        azureDevOpsService: com.example.azuredevops.service.AzureDevOpsService
    ): Map<String, Any> {
        val diff = azureDevOpsService.getFullDiff(id)
        val diffText = buildDiffText(diff)
        val review = aiReviewService.reviewPullRequest(diffText)
        return mapOf("pullRequestId" to id, "review" to review)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildDiffText(diffResult: Map<String, Any>): String {
        val files = diffResult["files"] as? List<Map<String, Any>> ?: return ""
        return files.joinToString("\n\n") { file ->
            val path = file["path"] ?: ""
            val diff = file["diff"] ?: ""
            "### $path\n$diff"
        }
    }
}

