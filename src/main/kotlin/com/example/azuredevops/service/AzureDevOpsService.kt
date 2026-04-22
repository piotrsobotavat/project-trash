package com.example.azuredevops.service

import com.example.azuredevops.config.AzureDevOpsProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Service
class AzureDevOpsService(
    @Qualifier("azureRestClient") private val restClient: RestClient,
    private val props: AzureDevOpsProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(AzureDevOpsService::class.java)
    }

    private val apiVersion = "api-version=7.1-preview.1"

    @PostConstruct
    fun init() {
        val (base, project) = parseOrgUrl()
        log.info("AzureDevOpsService initialized — base: $base | project: $project")
    }

    /** Splits orgUrl into Pair(baseUrl, project) */
    private fun parseOrgUrl(): Pair<String, String> {
        val url = props.orgUrl.trimEnd('/')
        val idx = url.lastIndexOf('/')
        require(idx > 0) { "Invalid org-url format: ${props.orgUrl}" }
        return url.substring(0, idx) to url.substring(idx + 1)
    }

    fun getRepositories(): List<Any> {
        val (base, project) = parseOrgUrl()
        val url = "$base/$project/_apis/git/repositories?$apiVersion"
        log.info("Fetching repositories from: $url")
        return try {
            val response = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map::class.java) ?: emptyMap<String, Any>()

            @Suppress("UNCHECKED_CAST")
            val result = (response["value"] as? List<Any>) ?: emptyList()
            log.info("Found ${result.size} repositories")
            result
        } catch (ex: RestClientException) {
            log.error("Failed to fetch repositories: ${ex.message}", ex)
            throw ex
        }
    }

    fun getPullRequests(
        repositoryId: String? = null,
        status: String = "active",
        top: Int = 10
    ): List<Any> {
        val (base, project) = parseOrgUrl()
        val url = if (repositoryId != null) {
            "$base/$project/_apis/git/repositories/$repositoryId/pullrequests" +
                    "?searchCriteria.status=$status&\$top=$top&$apiVersion"
        } else {
            "$base/$project/_apis/git/pullrequests" +
                    "?searchCriteria.status=$status&\$top=$top&$apiVersion"
        }
        log.info("Fetching pull requests [repositoryId=$repositoryId, status=$status, top=$top] from: $url")
        return try {
            val response = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map::class.java) ?: emptyMap<String, Any>()

            @Suppress("UNCHECKED_CAST")
            val result = (response["value"] as? List<Any>) ?: emptyList()
            log.info("Found ${result.size} pull requests")
            result
        } catch (ex: RestClientException) {
            log.error("Failed to fetch pull requests: ${ex.message}", ex)
            throw ex
        }
    }
}
