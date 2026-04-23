package com.example.azuredevops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AzureDevOpsApplication

fun main(args: Array<String>) {
    runApplication<AzureDevOpsApplication>(*args)
}

