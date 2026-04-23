package com.example.azuredevops.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Base64

@Configuration
class RestClientConfig(
    private val props: AzureDevOpsProperties,
    private val proxyProps: ProxyProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(RestClientConfig::class.java)
    }

    @Bean
    @Qualifier("azureRestClient")
    fun azureRestClient(): RestClient {
        require(props.pat.isNotBlank()) { "azure.devops.pat must not be blank in application.yml" }
        require(props.orgUrl.isNotBlank()) { "azure.devops.org-url must not be blank in application.yml" }

        val token = Base64.getEncoder().encodeToString(":${props.pat}".toByteArray())
        log.info("Azure DevOps RestClient configured for org-url: ${props.orgUrl}")
        log.info("PAT loaded: ${"*".repeat(props.pat.length - 4)}${props.pat.takeLast(4)}")

        val requestFactory = SimpleClientHttpRequestFactory()
        if (proxyProps.enabled && proxyProps.host.isNotBlank()) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyProps.host, proxyProps.port))
            requestFactory.setProxy(proxy)
            log.info("Using HTTP proxy: ${proxyProps.host}:${proxyProps.port}")
        } else {
            log.info("No proxy configured")
        }

        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("Authorization", "Basic $token")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
