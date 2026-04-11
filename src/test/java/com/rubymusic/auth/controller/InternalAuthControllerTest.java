package com.rubymusic.auth.controller;

import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.service.ServiceTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalAuthControllerTest {

    @Mock
    private ServiceTokenGenerator serviceTokenGenerator;

    @InjectMocks
    private InternalAuthController controller;

    private MockMvc mockMvc;

    private static final String VALID_SECRET  = "correct-secret";
    private static final String SERVICE_NAME  = "interaction-service";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalServiceSecret", VALID_SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validSecret_returns200() throws Exception {
        when(serviceTokenGenerator.generateServiceToken(SERVICE_NAME)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/auth/internal/service-token")
                        .header("X-Service-Name", SERVICE_NAME)
                        .header("X-Service-Secret", VALID_SECRET))
                .andExpect(status().isOk());
    }

    @Test
    void invalidSecret_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/internal/service-token")
                        .header("X-Service-Name", SERVICE_NAME)
                        .header("X-Service-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }
}
