package com.example.azuredevops.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "azure.openai")
data class AzureOpenAiProperties(
    val endpoint: String = "",
    val apiKey: String = "",
    val agentName: String = "PR-Review-Accelerator",
    val agentVersion: String = "3"
)

