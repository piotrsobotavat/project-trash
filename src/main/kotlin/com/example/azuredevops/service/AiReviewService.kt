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

        // Azure AI Foundry Responses API path
        private const val RESPONSES_PATH = "/openai/responses?api-version=2025-04-01-preview"
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
            "input" to listOf(
                mapOf("role" to "user", "content" to userMessage)
            ),
            "agent_reference" to mapOf(
                "name" to props.agentName,
                "version" to props.agentVersion,
                "type" to "agent_reference"
            )
        )

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

