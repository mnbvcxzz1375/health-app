package com.ahealth.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private AuthRepository authRepository;

  @Test
  void registerReturnsAuthSession() throws Exception {
    when(authService.register(any())).thenReturn(new AuthDtos.AuthResponse(
        "token_demo",
        new AuthDtos.AuthUserView("1", "李明", "liming@example.com", "")
    ));

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new AuthDtos.RegisterRequest(
                "李明",
                "liming@example.com",
                "123456"
            ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("token_demo"))
        .andExpect(jsonPath("$.user.name").value("李明"));
  }

  @Test
  void meReturnsCurrentUser() throws Exception {
    when(authService.me()).thenReturn(new AuthDtos.AuthSessionResponse(
        new AuthDtos.AuthUserView("1", "李明", "liming@example.com", "")
    ));

    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("liming@example.com"));
  }

  @Test
  void logoutReturnsSuccess() throws Exception {
    doNothing().when(authService).logout();

    mockMvc.perform(post("/api/auth/logout"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
