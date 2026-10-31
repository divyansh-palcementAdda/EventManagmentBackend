package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.gatekeeper.CreateGatekeeperRequest;
import com.app.eventmanagmentsystem.payload.gatekeeper.GatekeeperResponse;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.payload.pass.UpdatePassValidityRequest;
import com.app.eventmanagmentsystem.payload.pass.UpdatePassValidityResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.GatekeeperService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import com.app.eventmanagmentsystem.service.impl.PassValidityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PassValidityAndGatekeeperTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventDayRepository eventDayRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PassValidityService passValidityService;

    @Autowired
    private GatekeeperService gatekeeperService;

    @Autowired
    private PassGenerationService passGenerationService;

    private Event testEvent;
    private EventDay day1;
    private EventDay day2;
    private EventDay day3;
    private List<EventPass> passes;
    private User adminUser;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Admin", true)));
        Role gkRole = roleRepository.findByName("GATEKEEPER")
                .orElseGet(() -> roleRepository.save(new Role("GATEKEEPER", "Gatekeeper", true)));

        adminUser = new User();
        adminUser.setEmail("superadmin@eventpass.test");
        adminUser.setPasswordHash("hashed_pwd");
        adminUser.setFullName("Platform Admin");
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.saveAndFlush(adminUser);

        AppUserDetails adminDetails = new AppUserDetails(adminUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        testEvent = new Event();
        testEvent.setName("Tech Conference 2026");
        testEvent.setEventCode("TECH26");
        testEvent.setPassSeriesCode("GEN1");
        testEvent.setStatus(EventStatus.ACTIVE);
        testEvent.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        testEvent.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        testEvent.setMaximumPasses(100);
        testEvent.setTimezone("UTC");
        testEvent.setStartsAt(Instant.now());
        testEvent.setEndsAt(Instant.now().plusSeconds(86400 * 3));
        testEvent = eventRepository.saveAndFlush(testEvent);

        day1 = eventDayRepository.saveAndFlush(new EventDay(testEvent, 1, LocalDate.now(), "Day 1 - Keynote"));
        day2 = eventDayRepository.saveAndFlush(new EventDay(testEvent, 2, LocalDate.now().plusDays(1), "Day 2 - Workshops"));
        day3 = eventDayRepository.saveAndFlush(new EventDay(testEvent, 3, LocalDate.now().plusDays(2), "Day 3 - Hackathon"));

        passes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            EventPass pass = new EventPass();
            pass.setEvent(testEvent);
            pass.setPassNumber(String.format("TECH26-GEN1-%06d", i));
            pass.setTokenHash("hash_" + UUID.randomUUID());
            pass.setStatus(PassStatus.NOT_ACTIVATED);
            pass.setSequenceNumber((long) i);
            pass.setGeneratedAt(Instant.now());
            pass.setValidDays(new HashSet<>());
            passes.add(eventPassRepository.saveAndFlush(pass));
        }
    }

    @Test
    @DisplayName("Pass Validity: Update validity for individual passes")
    void updateValidityIndividual() {
        UpdatePassValidityRequest request = new UpdatePassValidityRequest();
        request.setPassIds(List.of(passes.get(0).getId(), passes.get(1).getId()));
        request.setValidDayIds(List.of(day1.getId(), day2.getId()));

        UpdatePassValidityResponse response = passValidityService.updatePassValidity(testEvent.getId(), request);

        assertThat(response.getUpdatedPassesCount()).isEqualTo(2);

        EventPass p1 = eventPassRepository.findById(passes.get(0).getId()).orElseThrow();
        assertThat(p1.getValidDays()).extracting(EventDay::getId).containsExactlyInAnyOrder(day1.getId(), day2.getId());

        EventPass p3 = eventPassRepository.findById(passes.get(2).getId()).orElseThrow();
        assertThat(p3.getValidDays()).isEmpty(); // Still unrestricted
    }

    @Test
    @DisplayName("Pass Validity: Update validity by sequence range")
    void updateValidityRange() {
        UpdatePassValidityRequest request = new UpdatePassValidityRequest();
        request.setFromSequence(2L);
        request.setToSequence(4L);
        request.setValidDayIds(List.of(day3.getId()));

        UpdatePassValidityResponse response = passValidityService.updatePassValidity(testEvent.getId(), request);

        assertThat(response.getUpdatedPassesCount()).isEqualTo(3);

        EventPass p2 = eventPassRepository.findById(passes.get(1).getId()).orElseThrow();
        assertThat(p2.getValidDays()).extracting(EventDay::getId).containsExactly(day3.getId());

        EventPass p4 = eventPassRepository.findById(passes.get(3).getId()).orElseThrow();
        assertThat(p4.getValidDays()).extracting(EventDay::getId).containsExactly(day3.getId());

        EventPass p1 = eventPassRepository.findById(passes.get(0).getId()).orElseThrow();
        assertThat(p1.getValidDays()).isEmpty();
    }

    @Test
    @DisplayName("Pass Validity: Update single pass validity and reset to all days")
    void updateSinglePassValidity() {
        EventPass target = passes.get(0);

        PassResponse res1 = passValidityService.updateSinglePassValidity(target.getId(), List.of(day1.getId()));
        assertThat(res1.getValidDays()).hasSize(1);

        // Reset to all days by passing empty list (all event days assigned)
        PassResponse res2 = passValidityService.updateSinglePassValidity(target.getId(), List.of());
        assertThat(res2.getValidDays()).hasSize(3);
    }

    @Test
    @DisplayName("Gatekeeper: Create and assign new gatekeeper")
    void createAndAssignGatekeeper() {
        CreateGatekeeperRequest request = new CreateGatekeeperRequest();
        request.setFullName("Alice Gatekeeper");
        request.setEmail("alice.gk@eventpass.test");
        request.setMobileNumber("+1234567890");
        request.setPassword("SecretPass123");

        GatekeeperResponse response = gatekeeperService.createAndAssignGatekeeper(testEvent.getId(), request);

        assertThat(response.getFullName()).isEqualTo("Alice Gatekeeper");
        assertThat(response.getEmail()).isEqualTo("alice.gk@eventpass.test");
        assertThat(response.isActive()).isTrue();

        List<GatekeeperResponse> assigned = gatekeeperService.getEventGatekeepers(testEvent.getId());
        assertThat(assigned).extracting(GatekeeperResponse::getEmail).contains("alice.gk@eventpass.test");
    }

    @Test
    @DisplayName("Gatekeeper: Search eligible gatekeepers and check alreadyAssigned flag")
    void searchEligibleGatekeepers() {
        CreateGatekeeperRequest r1 = new CreateGatekeeperRequest();
        r1.setFullName("Bob Scanner");
        r1.setEmail("bob.scanner@eventpass.test");
        gatekeeperService.createAndAssignGatekeeper(testEvent.getId(), r1);

        Role gkRole = roleRepository.findByName("GATEKEEPER").orElseThrow();
        User unassignedGk = new User();
        unassignedGk.setEmail("charlie.scanner@eventpass.test");
        unassignedGk.setFullName("Charlie Scanner");
        unassignedGk.setPasswordHash("pwd");
        unassignedGk.setRoles(new HashSet<>(Set.of(gkRole)));
        userRepository.saveAndFlush(unassignedGk);

        Page<GatekeeperResponse> page = gatekeeperService.searchEligibleGatekeepers(
                testEvent.getId(), "scanner", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSizeGreaterThanOrEqualTo(2);

        GatekeeperResponse bob = page.getContent().stream()
                .filter(g -> g.getEmail().equals("bob.scanner@eventpass.test"))
                .findFirst().orElseThrow();
        assertThat(bob.isAlreadyAssigned()).isTrue();

        GatekeeperResponse charlie = page.getContent().stream()
                .filter(g -> g.getEmail().equals("charlie.scanner@eventpass.test"))
                .findFirst().orElseThrow();
        assertThat(charlie.isAlreadyAssigned()).isFalse();
    }

    @Test
    @DisplayName("Pass Specification: Search passes by query, status, and sequence range")
    void searchPassesSpecification() {
        Page<PassResponse> result = passGenerationService.searchPassesByEvent(
                testEvent.getId(),
                "000003",
                PassStatus.NOT_ACTIVATED,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10, Sort.by("sequenceNumber").ascending())
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSequenceNumber()).isEqualTo(3L);
        assertThat(result.getContent().get(0).getPassNumber()).contains("000003");
    }
}
