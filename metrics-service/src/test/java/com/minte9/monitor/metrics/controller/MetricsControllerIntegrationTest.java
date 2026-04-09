package com.minte9.monitor.metrics.controller;

import com.minte9.monitor.metrics.repository.MetricMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MetricsControllerIntegrationTest {

    // Start a MongoDB Testcontainer for the duration of the tests
        @Container
        static final MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricMongoRepository metricMongoRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanDb() {
        metricMongoRepository.deleteAll();
    }

    @Test
    void shouldIngestAndReadBackMetric() throws Exception {
        String body = """
                {
                  "nodeId": "vps-01",
                  "metricType": "CPU",
                  "timestamp": "2026-03-29T10:15:30Z",
                  "payload": {
                    "usagePercent": 67.4,
                    "systemLoad": 1.82
                  }
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.nodeId").value("vps-01"))
                .andExpect(jsonPath("$.metricType").value("CPU"))
                .andExpect(jsonPath("$.payload.usagePercent").value(67.4));

        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/metrics/node/vps-01/latest/CPU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("vps-01"))
                .andExpect(jsonPath("$.metricType").value("CPU"));
    }

    @Test
    void shouldReturnBadRequestForInvalidPayload() throws Exception {
        String body = """
                {
                  "nodeId": "",
                  "metricType": "CPU",
                  "timestamp": "2026-03-29T10:15:30Z",
                  "payload": {}
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}