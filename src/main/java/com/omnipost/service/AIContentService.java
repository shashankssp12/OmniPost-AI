package com.omnipost.service;

import com.omnipost.config.OpenAIConfig;
import com.omnipost.dto.ContentMetadataDTO;
import com.omnipost.exception.VideoProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for generating AI-powered content metadata (title, caption, hashtags)
 * for uploaded videos using the OpenAI Chat Completions API.
 */
@Service
public class AIContentService {

    private static final Logger log = LoggerFactory.getLogger(AIContentService.class);
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";

    private final OpenAIConfig openAIConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIContentService(OpenAIConfig openAIConfig, RestTemplate restTemplate) {
        this.openAIConfig = openAIConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates AI-powered title, caption, and hashtags for the given video file name.
     *
     * @param videoFileName the original file name of the uploaded video
     * @return ContentMetadataDTO containing the generated title, caption, and hashtags
     */
    public ContentMetadataDTO generateContentMetadata(String videoFileName) {
        if (!openAIConfig.isConfigured()) {
            log.warn("OpenAI API key not configured. Using default content metadata.");
            return buildDefaultMetadata(videoFileName);
        }

        try {
            String prompt = buildPrompt(videoFileName);
            String responseText = callOpenAI(prompt);
            return parseContentMetadata(responseText);
        } catch (Exception e) {
            log.error("Failed to generate content metadata via OpenAI: {}", e.getMessage(), e);
            log.warn("Falling back to default content metadata.");
            return buildDefaultMetadata(videoFileName);
        }
    }

    private String buildPrompt(String videoFileName) {
        String baseName = videoFileName.replaceAll("\\.[^.]+$", "").replace("_", " ").replace("-", " ");
        return String.format(
                "You are a social media content creator. Based on the video file name \"%s\", " +
                "generate engaging social media content in the following JSON format only, with no extra text:\n" +
                "{\n" +
                "  \"title\": \"<catchy video title, max 100 chars>\",\n" +
                "  \"caption\": \"<engaging caption for the video, max 300 chars>\",\n" +
                "  \"hashtags\": [\"<hashtag1>\", \"<hashtag2>\", ... (5-10 relevant hashtags without # symbol)]\n" +
                "}",
                baseName);
    }

    private String callOpenAI(String prompt) {
        String url = openAIConfig.getBaseUrl() + CHAT_COMPLETIONS_ENDPOINT;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAIConfig.getApiKey());

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", openAIConfig.getModel());
        requestBody.put("max_tokens", openAIConfig.getMaxTokens());

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        } catch (Exception e) {
            throw new VideoProcessingException("Failed to serialize OpenAI request", e);
        }

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new VideoProcessingException("Failed to parse OpenAI response", e);
        }
    }

    private ContentMetadataDTO parseContentMetadata(String jsonResponse) {
        try {
            // Strip markdown code fences if present
            String cleaned = jsonResponse.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JsonNode root = objectMapper.readTree(cleaned);

            String title = root.path("title").asText("Untitled Video");
            String caption = root.path("caption").asText("");

            List<String> hashtags = new ArrayList<>();
            JsonNode hashtagsNode = root.path("hashtags");
            if (hashtagsNode.isArray()) {
                for (JsonNode tag : hashtagsNode) {
                    String tagText = tag.asText().replace("#", "").trim();
                    if (!tagText.isBlank()) {
                        hashtags.add(tagText);
                    }
                }
            }

            return ContentMetadataDTO.builder()
                    .title(title)
                    .caption(caption)
                    .hashtags(hashtags)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse AI response as JSON: {}", jsonResponse, e);
            return buildDefaultMetadata("video");
        }
    }

    private ContentMetadataDTO buildDefaultMetadata(String videoFileName) {
        String title = videoFileName.replaceAll("\\.[^.]+$", "").replace("_", " ").replace("-", " ");
        return ContentMetadataDTO.builder()
                .title(title)
                .caption("Check out this amazing video! 🎥 #trending")
                .hashtags(List.of("video", "trending", "viral", "content", "omnipost"))
                .build();
    }
}
