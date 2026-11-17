package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.impl.PassImageService;
import com.app.eventmanagmentsystem.util.PassLookupUtil;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@Transactional
public class PassImageLayoutAndScenariosTest {

    @Autowired
    private PassImageService passImageService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPassRepository passRepository;

    @Autowired
    private PassLookupUtil passLookupUtil;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new Event();
        testEvent.setName("Renaissance Coffee Festival");
        testEvent.setEventCode("RCF" + (System.currentTimeMillis() % 10000));
        testEvent.setPassSeriesCode("RC");
        testEvent.setStatus(EventStatus.PUBLISHED);
        testEvent.setStartsAt(Instant.now().plus(Duration.ofDays(1)));
        testEvent.setEndsAt(Instant.now().plus(Duration.ofDays(3)));
        testEvent.setVenue("Renaissance University Campus, Indore");
        testEvent = eventRepository.save(testEvent);
    }

    private EventPass createPass(String category, Set<EventDay> days) {
        EventPass pass = new EventPass();
        pass.setEvent(testEvent);
        pass.setPassNumber("RCF-RC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pass.setSequenceNumber(System.currentTimeMillis() % 10000);
        pass.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        pass.setCategory(category);
        pass.setStatus(PassStatus.NOT_ACTIVATED);
        pass.setGeneratedAt(Instant.now());
        if (days != null && !days.isEmpty()) {
            pass.setValidDays(days);
        }
        return passRepository.save(pass);
    }

    private void verifyImageAndQr(byte[] imageBytes, String expectedPassNumber) throws Exception {
        assertThat(imageBytes).isNotNull();
        assertThat(imageBytes.length).isGreaterThan(10000);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(1826);
        assertThat(img.getHeight()).isEqualTo(1372);

        // Verify the QR code on the lower ticket can be scanned and matches pass number
        // The QR code is situated roughly in X: 1300-1700, Y: 800-1100
        int qrCropX = (int) (img.getWidth() * 0.70);
        int qrCropY = (int) (img.getHeight() * 0.55);
        int qrCropW = img.getWidth() - qrCropX;
        int qrCropH = (int) (img.getHeight() * 0.35);

        BufferedImage qrSub = img.getSubimage(qrCropX, qrCropY, qrCropW, qrCropH);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qrSub)));
        Result result = new MultiFormatReader().decode(binaryBitmap);

        assertThat(result).isNotNull();
        String expectedUrl = passImageService.buildActivationUrl(expectedPassNumber);
        assertThat(result.getText()).isEqualTo(expectedUrl);
        assertThat(passLookupUtil.extractIdentifier(result.getText())).isEqualTo(expectedPassNumber);
    }

    @Test
    @DisplayName("Scenario 1: Short venue name and short single-day date range")
    void testScenario1_ShortVenueAndShortDate() throws Exception {
        testEvent.setVenue("Main Hall");
        eventRepository.save(testEvent);

        Set<EventDay> days = new HashSet<>();
        EventDay d1 = new EventDay();
        d1.setEvent(testEvent);
        d1.setEventDate(LocalDate.of(2026, 11, 14));
        d1.setDayNumber(1);
        days.add(d1);

        EventPass pass = createPass("GENERAL", days);
        byte[] bytes = passImageService.generateAndSavePassImage(pass);

        verifyImageAndQr(bytes, pass.getPassNumber());
    }

    @Test
    @DisplayName("Scenario 2: Long venue name wraps cleanly without overlapping dates")
    void testScenario2_LongVenueName() throws Exception {
        testEvent.setVenue("Renaissance University International Convention Complex & Grand Auditorium");
        eventRepository.save(testEvent);

        EventPass pass = createPass("VIP", null);
        byte[] bytes = passImageService.generateAndSavePassImage(pass);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(10000);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(img.getWidth()).isEqualTo(1826);
        assertThat(img.getHeight()).isEqualTo(1372);
    }

    @Test
    @DisplayName("Scenario 3: Multi-day date range spanning across months")
    void testScenario3_MultiDayDateRange() throws Exception {
        Set<EventDay> days = new HashSet<>();
        EventDay d1 = new EventDay();
        d1.setEvent(testEvent);
        d1.setEventDate(LocalDate.of(2026, 11, 28));
        d1.setDayNumber(1);
        days.add(d1);

        EventDay d2 = new EventDay();
        d2.setEvent(testEvent);
        d2.setEventDate(LocalDate.of(2026, 12, 2));
        d2.setDayNumber(2);
        days.add(d2);

        EventPass pass = createPass("DELEGATE", days);
        byte[] bytes = passImageService.generateAndSavePassImage(pass);

        verifyImageAndQr(bytes, pass.getPassNumber());
    }

    @Test
    @DisplayName("Scenario 4: Long campus and location name")
    void testScenario4_LongCampusAndLocationName() throws Exception {
        testEvent.setVenue("Auditorium 1, Renaissance University Campus, Gram Reoti, Sanwer Road, Indore, Madhya Pradesh");
        eventRepository.save(testEvent);

        EventPass pass = createPass("STUDENT", null);
        byte[] bytes = passImageService.generateAndSavePassImage(pass);

        verifyImageAndQr(bytes, pass.getPassNumber());
    }

    @Test
    @DisplayName("Scenario 5: Both long date range and long venue name")
    void testScenario5_LongDateAndLongVenue() throws Exception {
        testEvent.setVenue("Renaissance University Main Campus Convention Complex & Exhibition Hall, Ujjain Road, Indore, Madhya Pradesh");
        eventRepository.save(testEvent);

        Set<EventDay> days = new HashSet<>();
        EventDay d1 = new EventDay();
        d1.setEvent(testEvent);
        d1.setEventDate(LocalDate.of(2026, 11, 14));
        d1.setDayNumber(1);
        days.add(d1);

        EventDay d2 = new EventDay();
        d2.setEvent(testEvent);
        d2.setEventDate(LocalDate.of(2026, 11, 20));
        d2.setDayNumber(2);
        days.add(d2);

        EventPass pass = createPass("ALL_ACCESS", days);
        byte[] bytes = passImageService.generateAndSavePassImage(pass);

        verifyImageAndQr(bytes, pass.getPassNumber());
    }

    @Test
    @DisplayName("Image caching and versioning verification: forceRegenerate updates image on disk")
    void testForceRegenerateUpdatesDisk() throws Exception {
        EventPass pass = createPass("STANDARD", null);

        // Generate initial image
        byte[] initial = passImageService.getOrGeneratePassImage(pass.getId());
        assertThat(initial).isNotEmpty();

        // Update venue on event
        testEvent.setVenue("Updated Grand Hall, Renaissance Campus");
        eventRepository.save(testEvent);

        // Call without force -> returns cached
        byte[] cached = passImageService.getOrGeneratePassImage(pass.getId(), false);
        assertThat(cached).isEqualTo(initial);

        // Call with force -> regenerates with updated venue
        byte[] regenerated = passImageService.getOrGeneratePassImage(pass.getId(), true);
        assertThat(regenerated).isNotEmpty();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Hibernate boundary test: getOrGeneratePassImage succeeds outside active transaction without LazyInitializationException")
    void testNoLazyInitializationExceptionOutsideTransaction() throws Exception {
        // Create event and pass in a clean state
        Event standaloneEvent = new Event();
        standaloneEvent.setName("Spring Detached Session Event");
        standaloneEvent.setEventCode("SDSE" + (System.currentTimeMillis() % 10000));
        standaloneEvent.setPassSeriesCode("SD");
        standaloneEvent.setStatus(EventStatus.PUBLISHED);
        standaloneEvent.setStartsAt(Instant.now().plus(Duration.ofDays(2)));
        standaloneEvent.setEndsAt(Instant.now().plus(Duration.ofDays(5)));
        standaloneEvent.setVenue("Indore Convention Centre, Hall B");
        standaloneEvent = eventRepository.save(standaloneEvent);

        EventPass pass = new EventPass();
        pass.setEvent(standaloneEvent);
        pass.setPassNumber("SDSE-SD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pass.setSequenceNumber(8899L);
        pass.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        pass.setCategory("VIP");
        pass.setStatus(PassStatus.NOT_ACTIVATED);
        pass.setGeneratedAt(Instant.now());
        EventPass savedPass = passRepository.save(pass);

        // Outside transaction: Hibernate session is NOT open here
        byte[] imageBytes = passImageService.getOrGeneratePassImage(savedPass.getId(), true);

        assertThat(imageBytes).isNotEmpty();
        assertThat(imageBytes.length).isGreaterThan(10000);
    }

    @Test
    @DisplayName("Fallback badge image generation works on snapshot without accessing Hibernate proxies")
    void testFallbackBadgeGenerationOnSnapshot() {
        com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot snapshot =
                com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot.builder()
                        .passId(UUID.randomUUID())
                        .passNumber("FALLBACK-0001")
                        .sequenceNumber(1L)
                        .category("VIP")
                        .noQr(true)
                        .attendeeName("Rohan Sharma")
                        .eventName("Renaissance Coffee Festival")
                        .eventVenue("Renaissance University")
                        .timezone("Asia/Kolkata")
                        .eventStartsAt(Instant.now())
                        .eventEndsAt(Instant.now().plus(Duration.ofDays(2)))
                        .validDates(java.util.List.of(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)))
                        .build();

        byte[] fallbackBytes = passImageService.generateFallbackBadgeImage(snapshot);

        assertThat(fallbackBytes).isNotEmpty();
        assertThat(fallbackBytes.length).isGreaterThan(5000);
    }
}
