package com.adobe.sign.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.retry.annotation.Backoff;

import java.nio.file.Files;
import java.util.Collections;

@Service
public class AdobeSignService {

    @Autowired
    private final RestTemplate restTemplate;

    @Autowired
    private final ObjectMapper objectMapper;

    public AdobeSignService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${adobe.auth.url}")
    private String baseUrl;

    @Value("${adobe.api.key}")
    private String apiKey;

    @Value("${redirect.url}")
    private String redirectUrl;

    @Value("${adobe.email}")
    private String email;

    @Value("${adobe.retry.maxAttempts}")
    private int maxAttempts;

    @Value("${adobe.retry.delay}")
    private int delay;

    public String uploadDocument(MultipartFile file) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("File-Name", file.getOriginalFilename());
        body.add("File", new HttpEntity<>(file.getBytes(), headers));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String uploadUrl = baseUrl + "/transientDocuments";
        ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

        // Extract transientDocumentId from JSON response
        String responseBody = response.getBody();
        String transientDocumentId = parseTransientDocumentId(responseBody);
        return transientDocumentId;
    }

    public String createAgreement(String transientDocumentId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        String payload = buildAgreementPayload(transientDocumentId);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        String agreementUrl = baseUrl + "/agreements";
        ResponseEntity<String> response = restTemplate.postForEntity(agreementUrl, entity, String.class);

        String responseBody = response.getBody();
        String agreementId = parseAgreementId(responseBody);
        return agreementId;
    }

    @Retryable(value = HttpClientErrorException.NotFound.class, maxAttemptsExpression = "#{${adobe.retry.maxAttempts}}", backoff = @Backoff(delayExpression = "#{${adobe.retry.delay}}"))
    public String getSigningUrl(String agreementId) throws Exception {

        try {

            System.out.println("Retry");
            return externalApiCallToGetSigningUrl(agreementId);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Log this exception or handle it accordingly
                System.out.println("Retry on error");
                throw e;
            }
            // Re-throw if it's not a 404 error
            throw new RuntimeException("API call failed with different error", e);
        }

    }

    private String externalApiCallToGetSigningUrl(String agreementId) {
        // TODO Auto-generated method stub
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);

        String signingUrl = baseUrl + "/agreements/" + agreementId + "/signingUrls";
        ResponseEntity<String> response = restTemplate.exchange(signingUrl, HttpMethod.GET, new HttpEntity<>(headers),
                String.class);

        String responseBody = response.getBody();
        String esignUrl = "";
        try {
            esignUrl = parseEsignUrl(responseBody);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return esignUrl;
    }

    public byte[] downloadSignedDocument(String agreementId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));
        headers.setBearerAuth(apiKey);

        String downloadUrl = baseUrl + "/agreements/" + agreementId + "/combinedDocument";
        ResponseEntity<byte[]> response = restTemplate.exchange(downloadUrl, HttpMethod.GET, new HttpEntity<>(headers),
                byte[].class);

        return response.getBody();
    }

    private String parseTransientDocumentId(String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        return rootNode.path("transientDocumentId").asText();
    }

    private String buildAgreementPayload(String transientDocumentId) {
        return "{\n" +
                "  \"fileInfos\": [\n" +
                "    {\n" +
                "      \"transientDocumentId\": \"" + transientDocumentId + "\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"name\": \"TestAgreement\",\n" +
                "  \"participantSetsInfo\": [\n" +
                "    {\n" +
                "      \"memberInfos\": [\n" +
                "        {\n" +
                "          \"email\":  \"" + email + "\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"order\": 1,\n" +
                "      \"role\": \"SIGNER\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"signatureType\": \"ESIGN\",\n" +
                "  \"state\": \"IN_PROCESS\",\n" +
                "  \"locale\": \"en_US\",\n" +
                "  \"status\": \"OUT_FOR_SIGNATURE\",\n" +
                "  \"documentVisibilityEnabled\": false,\n" +
                "  \"hasFormFieldData\": false,\n" +
                "  \"hasSignerIdentityReport\": false,\n" +
                "  \"agreementSettingsInfo\": {\n" +
                "    \"canEditFiles\": true,\n" +
                "    \"canEditElectronicSeals\": true,\n" +
                "    \"canEditAgreementSettings\": true,\n" +
                "    \"showAgreementReminderSentEvents\": false,\n" +
                "    \"hipaaEnabled\": false,\n" +
                "    \"showDocumentsViewedEvents\": false\n" +
                "  },\n" +
                "  \"sendType\": \"REGULAR_SEND\",\n" +
                "  \"documentRetentionApplied\": false,\n" +
                "  \"postSignOption\": {\n" +
                "    \"redirectDelay\": 0,\n" +
                "    \"redirectUrl\": \"" + redirectUrl + "\"\n" +
                "  }\n" +
                "}";

    }

    private String parseAgreementId(String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        return rootNode.path("id").asText();
    }

    private String parseEsignUrl(String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode signingUrlSetInfos = rootNode.path("signingUrlSetInfos");
        if (signingUrlSetInfos.isArray() && signingUrlSetInfos.has(0)) {
            JsonNode signingUrls = signingUrlSetInfos.get(0).path("signingUrls");
            if (signingUrls.isArray() && signingUrls.has(0)) {
                return signingUrls.get(0).path("esignUrl").asText();
            }
        }
        return null; // or throw an exception if appropriate
    }
}
