package com.example.azuredevops.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "azure.openai")
data class AzureOpenAiProperties(
    val endpoint: String = "",
    val apiKey: String = "",
    val agentName: String = "PR-Review-Accelerator",
    val agentVersion: String = "4",
    val apiVersion: String = "2025-03-01-preview",
    val model: String = "gpt-4o"
)

