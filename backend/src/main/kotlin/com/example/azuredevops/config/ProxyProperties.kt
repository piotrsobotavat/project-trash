package com.example.azuredevops.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "proxy")
data class ProxyProperties(
    val host: String = "",
    val port: Int = 8080,
    val enabled: Boolean = true
)

