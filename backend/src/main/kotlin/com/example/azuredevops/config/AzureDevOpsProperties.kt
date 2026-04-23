package com.example.azuredevops.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "azure.devops")
data class AzureDevOpsProperties(
    val orgUrl: String = "",
    val pat: String = ""
)

