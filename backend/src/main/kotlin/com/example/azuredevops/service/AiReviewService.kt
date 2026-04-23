package com.example.azuredevops.service

import com.example.azuredevops.config.AzureOpenAiProperties
import com.example.azuredevops.config.ProxyProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Service that communicates with the Azure AI Foundry PR-Review-Accelerator agent
 * using the OpenAI Responses API.
 *
 * Kotlin equivalent of:
 *   from azure.ai.projects import AIProjectClient
 *   openai_client = project_client.get_openai_client()
 *   response = openai_client.responses.create(
 *       input=[{"role": "user", "content": "..."}],
 *       extra_body={"agent_reference": {"name": my_agent, "version": my_version, "type": "agent_reference"}},
 *   )
 */
@Service
class AiReviewService(
    private val props: AzureOpenAiProperties,
    private val proxyProps: ProxyProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(AiReviewService::class.java)

        // Azure AI Foundry Responses API path (v1)
        private const val RESPONSES_PATH = "/openai/v1/responses"

        // All known Azure OpenAI / AI Foundry API versions to probe
        val CANDIDATE_VERSIONS = listOf(
            "2025-04-15-preview",
            "2025-04-01-preview",
            "2025-03-01-preview",
            "2025-02-01-preview",
            "2025-01-01-preview",
            "2024-12-01-preview",
            "2024-10-01-preview",
            "2024-09-01-preview",
            "2024-08-01-preview",
            "2024-07-01-preview",
            "2024-05-01-preview",
            "2024-04-01-preview",
            "2024-02-15-preview",
            "2024-02-01",
            "2023-12-01-preview",
            "2023-09-01-preview",
            "2023-07-01-preview",
            "2023-06-01-preview",
            "2023-05-15",
            "2022-12-01"
        )
    }

    private val openAiRestClient: RestClient by lazy {
        val factory = SimpleClientHttpRequestFactory()
        if (proxyProps.enabled && proxyProps.host.isNotBlank()) {
            factory.setProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyProps.host, proxyProps.port)))
            log.info("AiReviewService using proxy: ${proxyProps.host}:${proxyProps.port}")
        }
        RestClient.builder()
            .requestFactory(factory)
            .baseUrl(props.endpoint.trimEnd('/'))
            .defaultHeader("api-key", props.apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build()
    }

    @PostConstruct
    fun init() {
        log.info("AiReviewService initialized — endpoint: ${props.endpoint} | agent: ${props.agentName} v${props.agentVersion}")
    }

    /**
     * Sends a message to the PR-Review-Accelerator agent and returns its response text.
     */
    fun chat(userMessage: String): String {
        log.info("Sending message to agent '${props.agentName}': $userMessage")

        val requestBody = mapOf(
            "model" to props.model,
            "input" to listOf(
                mapOf("role" to "user", "content" to userMessage)
            )
        )

        log.info(RESPONSES_PATH)
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = openAiRestClient.post()
                .uri(RESPONSES_PATH)
                .body(requestBody)
                .retrieve()
                .body(Map::class.java) as? Map<String, Any>
                ?: error("Empty response from agent")

            val outputText = extractOutputText(response)
            log.info("Agent response: $outputText")
            outputText
        } catch (ex: RestClientException) {
            log.error("Failed to call PR-Review-Accelerator agent: ${ex.message}", ex)
            throw ex
        }
    }

    /**
     * Sends a simple "hello" to the PR-Review-Accelerator agent.
     */
    fun sayHello(): String = chat("Hello! Tell me what you can help with.")

    /**
     * Asks the PR-Review-Accelerator agent to review a pull request diff.
     */
    fun reviewPullRequest(diffContent: String): String =
        chat("Please review the following pull request diff and provide feedback:\n\n$diffContent")

    // -----------------------------------------------------------------------
    // Deployment discovery
    // -----------------------------------------------------------------------

    /**
     * Lists all model deployments available in this Azure OpenAI resource.
     * Use the returned "id" value as the model name in application-lcl.yml.
     */
    fun listDeployments(): List<Map<String, Any>> {
        // Try a few known api-versions for the deployments endpoint
        val versions = listOf(props.apiVersion)
        for (version in versions) {
            val uri = "/openai/deployments?api-version=$version"
            log.info("Listing deployments via GET ${props.endpoint.trimEnd('/')}$uri")
            try {
                @Suppress("UNCHECKED_CAST")
                val response = openAiRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(Map::class.java) as? Map<String, Any> ?: continue
                @Suppress("UNCHECKED_CAST")
                val list = response["data"] as? List<Map<String, Any>> ?: emptyList()
                log.info("Found ${list.size} deployments: ${list.map { it["id"] }}")
                return list
            } catch (ex: Exception) {
                log.warn("Could not list deployments with api-version=$version: ${ex.message?.take(100)}")
            }
        }
        return emptyList()
    }

    // -----------------------------------------------------------------------
    // API version probe
    // -----------------------------------------------------------------------

    /**
     * Tries every known API version in sequence and logs the result for each.
     * Returns a map of version -> outcome ("OK" / error message).
     */
    fun probeApiVersions(): Map<String, String> {
        val testBody = mapOf(
            "model" to props.model,
            "input" to listOf(mapOf("role" to "user", "content" to "ping"))
        )

        val results = linkedMapOf<String, String>()

        log.info("=== Starting API version probe (${CANDIDATE_VERSIONS.size} versions) ===")
        for (version in CANDIDATE_VERSIONS) {
            val uri = "$RESPONSES_PATH?api-version=$version"
            log.info("Trying api-version=$version  →  POST ${props.endpoint.trimEnd('/')}$uri")
            try {
                @Suppress("UNCHECKED_CAST")
                val response = openAiRestClient.post()
                    .uri(uri)
                    .body(testBody)
                    .retrieve()
                    .body(Map::class.java) as? Map<String, Any>

                val text = if (response != null) extractOutputText(response) else "(empty body)"
                log.info("  ✅ api-version=$version  →  SUCCESS: $text")
                results[version] = "OK: $text"
            } catch (ex: Exception) {
                val msg = ex.message ?: ex.javaClass.simpleName
                log.warn("  ❌ api-version=$version  →  ${msg.take(120)}")
                results[version] = msg.take(200)
            }
        }

        log.info("=== Probe complete ===")
        val working = results.filter { it.value.startsWith("OK") }
        if (working.isNotEmpty()) {
            log.info("Working versions: ${working.keys}")
        } else {
            log.warn("No working version found. Set the correct one in application-lcl.yml → azure.openai.api-version")
        }

        return results
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts the text from the Responses API output.
     * The response schema is:
     *   { "output": [ { "content": [ { "text": "..." } ] } ] }
     * or (simple):
     *   { "output_text": "..." }
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractOutputText(response: Map<String, Any>): String {
        // Try flat output_text first
        (response["output_text"] as? String)?.let { return it }

        // Try structured output array
        val output = response["output"] as? List<Map<String, Any>> ?: return response.toString()
        for (item in output) {
            val content = item["content"] as? List<Map<String, Any>> ?: continue
            for (block in content) {
                val text = block["text"] as? String ?: continue
                return text
            }
        }
        return response.toString()
    }
}

