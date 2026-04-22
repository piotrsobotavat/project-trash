package com.example.azuredevops.service

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
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

    fun getPullRequestDiff(pullRequestId: Int): Map<String, Any> {
        val (base, project) = parseOrgUrl()

        // 1. Fetch PR to resolve repositoryId
        val prUrl = "$base/$project/_apis/git/pullrequests/$pullRequestId?$apiVersion"
        log.info("Fetching PR details from: $prUrl")
        val pr = restClient.get().uri(prUrl).retrieve().body(Map::class.java)
            ?: error("PR $pullRequestId not found")

        @Suppress("UNCHECKED_CAST")
        val repo = pr["repository"] as? Map<String, Any>
            ?: error("PR $pullRequestId has no repository info")
        val repositoryId = repo["id"] as? String
            ?: error("PR $pullRequestId repository has no id")
        log.info("PR $pullRequestId belongs to repository: $repositoryId")

        // 2. Fetch iterations and pick the latest
        val iterUrl = "$base/$project/_apis/git/repositories/$repositoryId/pullRequests/$pullRequestId/iterations?$apiVersion"
        log.info("Fetching iterations from: $iterUrl")
        val iterResponse = restClient.get().uri(iterUrl).retrieve().body(Map::class.java)
            ?: error("No iterations found for PR $pullRequestId")

        @Suppress("UNCHECKED_CAST")
        val iterations = (iterResponse["value"] as? List<Map<String, Any>>) ?: emptyList()
        val latestIteration = iterations.maxByOrNull { (it["id"] as? Int) ?: 0 }
            ?: error("No iterations available for PR $pullRequestId")
        val iterationId = latestIteration["id"] as? Int
            ?: error("Iteration has no id")
        log.info("Using latest iteration id: $iterationId")

        // 3. Fetch changes for the latest iteration
        val changesUrl = "$base/$project/_apis/git/repositories/$repositoryId" +
                "/pullRequests/$pullRequestId/iterations/$iterationId/changes?$apiVersion"
        log.info("Fetching diff from: $changesUrl")
        val changesResponse = restClient.get().uri(changesUrl).retrieve().body(Map::class.java)
            ?: emptyMap<String, Any>()

        @Suppress("UNCHECKED_CAST")
        val changeEntries = (changesResponse["changeEntries"] as? List<Any>) ?: emptyList()
        log.info("Found ${changeEntries.size} changed files in PR $pullRequestId (iteration $iterationId)")

        return mapOf(
            "pullRequestId" to pullRequestId,
            "repositoryId" to repositoryId,
            "iterationId" to iterationId,
            "totalChanges" to changeEntries.size,
            "changes" to changeEntries
        )
    }

    fun getFullDiff(pullRequestId: Int): Map<String, Any> {
        val diffResult = getPullRequestDiff(pullRequestId)
        val repositoryId = diffResult["repositoryId"] as String
        val (base, project) = parseOrgUrl()

        @Suppress("UNCHECKED_CAST")
        val changes = diffResult["changes"] as List<Map<String, Any>>

        val fileDiffs = changes.mapNotNull { change ->
            val changeType = change["changeType"] as? String ?: return@mapNotNull null

            @Suppress("UNCHECKED_CAST")
            val item = change["item"] as? Map<String, Any> ?: return@mapNotNull null
            val path = item["path"] as? String ?: return@mapNotNull null
            val newObjectId = item["objectId"] as? String
            val originalObjectId = item["originalObjectId"] as? String

            log.info("Fetching diff for file: $path (changeType=$changeType)")

            val originalLines = if (originalObjectId != null && changeType != "add") {
                fetchBlobContent(base, project, repositoryId, originalObjectId)
            } else emptyList()

            val newLines = if (newObjectId != null && changeType != "delete") {
                fetchBlobContent(base, project, repositoryId, newObjectId)
            } else emptyList()

            val patch = DiffUtils.diff(originalLines, newLines)
            val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                "a$path", "b$path", originalLines, patch, 3
            )

            mapOf(
                "path" to path,
                "changeType" to changeType,
                "linesAdded" to patch.deltas.sumOf { it.target.lines.size },
                "linesRemoved" to patch.deltas.sumOf { it.source.lines.size },
                "diff" to unifiedDiff.joinToString("\n")
            )
        }

        return mapOf(
            "pullRequestId" to pullRequestId,
            "repositoryId" to repositoryId,
            "totalFiles" to fileDiffs.size,
            "files" to fileDiffs
        )
    }

    private fun fetchBlobContent(
        base: String,
        project: String,
        repositoryId: String,
        objectId: String
    ): List<String> {
        val url = "$base/$project/_apis/git/repositories/$repositoryId/blobs/$objectId?\$format=text&$apiVersion"
        log.debug("Fetching blob: $url")
        return try {
            val content = restClient.get()
                .uri(url)
                .retrieve()
                .body(String::class.java) ?: ""
            content.lines()
        } catch (ex: RestClientException) {
            log.warn("Could not fetch blob $objectId: ${ex.message}")
            emptyList()
        }
    }
}
