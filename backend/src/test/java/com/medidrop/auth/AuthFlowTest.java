package com.medidrop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndProtectedAccess() throws Exception {
        String body = """
                {"fullName":"Test Customer","email":"auth.test@medidrop.local",
                 "phone":"9811112222","password":"Secret123"}
                """;
        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.emailVerified").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(registerResponse);
        String accessToken = node.path("data").path("accessToken").asText();
        String refreshToken = node.path("data").path("refreshToken").asText();

        mockMvc.perform(get("/api/v1/customer/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/customer/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));

        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"auth.test@medidrop.local\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"auth.test@medidrop.local\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Old refresh token must not work after rotation.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otpEndpointsDoNotRevealWhetherAnAccountExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-account@medidrop.local\",\"purpose\":\"EMAIL_VERIFICATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message")
                        .value("If the account exists, a verification code has been sent"));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-account@medidrop.local\",\"purpose\":\"EMAIL_VERIFICATION\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code"));
    }

    @Test
    void pharmacyRegistrationDoesNotEchoThePassword() throws Exception {
        String response = mockMvc.perform(post("/api/v1/pharmacies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pharmacyName":"Deep Scan Pharmacy","ownerName":"Deep Scan Owner",
                                 "email":"pharmacy.deepscan@medidrop.local","phone":"9833334444",
                                 "password":"Secret123","licenseNumber":"WB-DEEP-SCAN-01",
                                 "registeredAddress":"1 Test Road","city":"Kolkata","state":"West Bengal",
                                 "pincode":"700001","latitude":22.5726,"longitude":88.3639,
                                 "deliveryRadiusKm":5}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("password", "Secret123");
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String body = """
                {"fullName":"Duplicate User","email":"duplicate@medidrop.local",
                 "phone":"9822223333","password":"Secret123"}
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
