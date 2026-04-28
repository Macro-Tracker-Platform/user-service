package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.olehprukhnytskyi.macrotrackeruserservice.config.AbstractRedisTest;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.job.OutboxJob;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.CustomHeaders;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import java.security.KeyPair;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Sql(scripts = "classpath:database/add-user-details-for-profile.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:database/remove-user-details-for-profile.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserProfileControllerTest extends AbstractRedisTest {
    protected static MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private UserRepository userRepository;
    @MockitoSpyBean
    private UserProfileRepository userProfileRepository;

    @MockitoBean
    private OutboxRepository outboxRepository;
    @InjectMocks
    private UserProfileService userProfileService;
    @MockitoBean
    private RSAKey rsaKey;
    @MockitoBean
    private JWKSet jwkSet;
    @MockitoBean
    private KeyPair keyPair;
    @MockitoBean
    private RSASSASigner rsassaSigner;
    @MockitoBean
    private OutboxJob outboxJob;
    @MockitoBean
    private LockProvider lockProvider;

    @BeforeAll
    static void beforeAll(
            @Autowired WebApplicationContext applicationContext
    ) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .build();
    }

    @Test
    @DisplayName("When valid userId, should return user details")
    void getUserDetails_whenValidUserId_shouldReturnUserDetails() throws Exception {
        // Given
        UserDetailsResponseDto userDetailsResponseDto = UserDetailsResponseDto.builder()
                .activityLevel(ActivityLevel.LIGHTLY_ACTIVE)
                .age(20)
                .gender(Gender.MALE)
                .goal(Goal.LOSE)
                .height(190)
                .weight(90)
                .bodyType(BodyType.NORMAL)
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                        get("/api/profile/details")
                                .header(CustomHeaders.X_USER_ID, 2)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(userDetailsResponseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When not existing userId, should return 404 status")
    void getUserDetails_whenNotExistingUserId_shouldReturn404Status() throws Exception {
        // When
        mockMvc.perform(
                        get("/api/profile/details")
                                .header(CustomHeaders.X_USER_ID, 100)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("When valid userId, should return user goal")
    void getUserGoal_whenValidUserId_shouldReturnUserGoal() throws Exception {
        // Given
        GoalResponseDto goalResponseDto = GoalResponseDto
                .builder()
                .calories(3000)
                .carbohydrates(300)
                .fat(80)
                .protein(130)
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                        get("/api/profile/goal")
                                .header(CustomHeaders.X_USER_ID, 1)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(goalResponseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When not existing userId, should return 404 status")
    void getUserGoal_whenNotExistingUserId_shouldReturn404Status() throws Exception {
        // When
        mockMvc.perform(
                        get("/api/profile/goal")
                                .header(CustomHeaders.X_USER_ID, 100)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("When valid userId, should delete user")
    void deleteUser_whenValidUserId_shouldDeleteUser() throws Exception {
        // Given
        assertThat(userRepository.findById(2L)).isPresent();

        // When
        mockMvc.perform(
                        delete("/api/profile")
                                .header(CustomHeaders.X_USER_ID, 2)
                )
                .andExpect(status().isNoContent());

        // Then
        assertThat(userRepository.findById(2L)).isEmpty();
        verify(userRepository, times(1)).deleteById(2L);
        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("When not existing userId, should return 204 status")
    void deleteUser_whenNotExistingUserId_shouldReturn404Status() throws Exception {
        // When
        mockMvc.perform(
                        delete("/api/profile")
                                .header(CustomHeaders.X_USER_ID, 100)
                )
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("When recalculate=true, should call goalClient and update profile")
    void updateUserDetails_whenRecalculateTrue_shouldUpdateGoalAndProfile() throws Exception {
        // Given
        Long userId = 1L;
        UpdateUserDetailsRequestDto requestDto = UpdateUserDetailsRequestDto.builder()
                .age(51)
                .weight(70)
                .height(180)
                .gender(Gender.MALE)
                .activityLevel(ActivityLevel.EXTRA_ACTIVE)
                .goal(Goal.LOSE)
                .bodyType(BodyType.NORMAL)
                .recalculate(true)
                .build();
        String requestJson = objectMapper.writeValueAsString(requestDto);
        UserDetailsResponseDto userDetailsResponseDto = UserDetailsResponseDto.builder()
                .age(51)
                .weight(70)
                .height(180)
                .gender(Gender.MALE)
                .bodyType(BodyType.NORMAL)
                .activityLevel(ActivityLevel.EXTRA_ACTIVE)
                .goal(Goal.LOSE)
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                        patch("/api/profile/details")
                                .header(CustomHeaders.X_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(userDetailsResponseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When recalculate=false, should only update details")
    void updateUserDetails_whenRecalculateFalse_shouldUpdateDetailsOnly() throws Exception {
        // Given
        Long userId = 2L;
        UpdateUserDetailsRequestDto requestDto = UpdateUserDetailsRequestDto.builder()
                .age(30)
                .weight(80)
                .height(175)
                .gender(Gender.FEMALE)
                .activityLevel(ActivityLevel.LIGHTLY_ACTIVE)
                .goal(Goal.MAINTAIN)
                .bodyType(BodyType.NORMAL)
                .recalculate(false)
                .build();
        String requestJson = objectMapper.writeValueAsString(requestDto);
        UserDetailsResponseDto userDetailsResponseDto = UserDetailsResponseDto.builder()
                .age(30)
                .weight(80)
                .height(175)
                .gender(Gender.FEMALE)
                .bodyType(BodyType.NORMAL)
                .activityLevel(ActivityLevel.LIGHTLY_ACTIVE)
                .goal(Goal.MAINTAIN)
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                        patch("/api/profile/details")
                                .header(CustomHeaders.X_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(userDetailsResponseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When valid request, should update goal and return updated goal")
    void updateGoal_whenValidRequest_shouldUpdateAndReturn() throws Exception {
        // Given
        Long userId = 1L;
        UpdateGoalRequestDto requestDto = new UpdateGoalRequestDto();
        requestDto.setCalories(2200);
        requestDto.setProtein(120);
        requestDto.setCarbohydrates(250);
        requestDto.setFat(70);
        String requestJson = objectMapper.writeValueAsString(requestDto);

        // When
        MvcResult mvcResult = mockMvc.perform(
                        patch("/api/profile/goal")
                                .header(CustomHeaders.X_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(requestDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());

        verify(userProfileRepository, times(1)).findById(userId);
        verify(userProfileRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("When not existing userId, should return 200 status")
    void updateGoal_whenNotExistingUserId_shouldReturn404Status() throws Exception {
        // When
        mockMvc.perform(
                        get("/api/profile/goal")
                                .header(CustomHeaders.X_USER_ID, 100)
                )
                .andExpect(status().isNotFound());
    }
}
