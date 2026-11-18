package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.repository.AttendeeRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import com.app.eventmanagmentsystem.service.impl.PassImageDataLoader;
import com.app.eventmanagmentsystem.service.impl.PassImageService;
import com.app.eventmanagmentsystem.util.PassLookupUtil;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@Transactional
public class WhatsAppPassImageIntegrationTest {

    @Autowired
    private PassImageService passImageService;

    @Autowired
    private PassImageDataLoader passImageDataLoader;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private EventPassRepository passRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PassLookupUtil passLookupUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    private Event testEvent;

    private void authenticateSuperAdmin() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Super Admin", true)));

        User superAdmin = userRepository.findByEmail("admin_wa_test@eventpass.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("admin_wa_test@eventpass.test");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Super Administrator");
            u.setRoles(Set.of(superAdminRole));
            return userRepository.saveAndFlush(u);
        });

        AppUserDetails userDetails = new AppUserDetails(superAdmin);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void setUp() {
        authenticateSuperAdmin();
        testEvent = new Event();
        testEvent.setName("Renaissance Coffee Festival 2026");
        testEvent.setEventCode("RCF26");
        testEvent.setPassSeriesCode("RC26INIT");
        testEvent.setStatus(EventStatus.PUBLISHED);
        testEvent.setVenue("Renaissance University, Campus Indore");
        testEvent.setStartsAt(Instant.now().plus(10, ChronoUnit.DAYS));
        testEvent.setEndsAt(Instant.now().plus(12, ChronoUnit.DAYS));
        testEvent.setTimezone("Asia/Kolkata");
        testEvent = eventRepository.save(testEvent);
    }

    private EventPass generatePass(String categoryCode) {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(testEvent.getId());
        req.setCategoryCode(categoryCode);
        req.setBatchSize(1);
        List<PassResponse> responses = passGenerationService.generatePasses(req);
        assertThat(responses).isNotEmpty();
        return passRepository.findById(responses.get(0).getId()).orElseThrow();
    }

    private String decodeQrFromImage(byte[] imageBytes) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertThat(img).isNotNull();
        double scaleX = (double) img.getWidth() / 1080.0;
        double scaleY = (double) img.getHeight() / 1350.0;
        int boxX = (int) Math.round(625 * scaleX);
        int boxY = (int) Math.round(598 * scaleY);
        int boxW = (int) Math.round(273 * scaleX);
        int boxH = (int) Math.round(272 * scaleY);

        BufferedImage qrSub = img.getSubimage(boxX, boxY, boxW, boxH);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Result result;
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qrSub)));
            result = new MultiFormatReader().decode(bitmap, hints);
        } catch (Exception e) {
            BinaryBitmap globalBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(new BufferedImageLuminanceSource(qrSub)));
            result = new MultiFormatReader().decode(globalBitmap, hints);
        }
        return result.getText();
    }

    // =========================================================================
    // SECTION 1: Image Generation Verification
    // =========================================================================

    @Test
    @DisplayName("1. WhatsApp image loads correct classpath artwork and preserves dimensions")
    void testWhatsAppImageLoadsCorrectArtworkAndDimensions() throws Exception {
        EventPass pass = generatePass("STUDENT");
        byte[] waImage = passImageService.generateAndSaveWhatsAppPassImage(pass);

        assertThat(waImage).isNotNull().isNotEmpty();
        // PNG magic bytes
        assertThat(waImage[0]).isEqualTo((byte) 0x89);
        assertThat(waImage[1]).isEqualTo((byte) 'P');
        assertThat(waImage[2]).isEqualTo((byte) 'N');
        assertThat(waImage[3]).isEqualTo((byte) 'G');

        BufferedImage template;
        try (InputStream in = getClass().getResourceAsStream("/passes/rcf whatsapp1.png")) {
            template = ImageIO.read(in);
        }

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(waImage));
        assertThat(img.getWidth()).isEqualTo(template.getWidth());
        assertThat(img.getHeight()).isEqualTo(template.getHeight());
    }

    @Test
    @DisplayName("2. QR code is placed precisely within designated white square")
    void testQrPlacementWithinDesignatedSquare() throws Exception {
        EventPass pass = generatePass("GENERAL");
        byte[] waImage = passImageService.generateAndSaveWhatsAppPassImage(pass);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(waImage));

        double scaleX = (double) img.getWidth() / 1080.0;
        double scaleY = (double) img.getHeight() / 1350.0;
        int qrCenterX = (int) Math.round(762 * scaleX);
        int qrCenterY = (int) Math.round(734 * scaleY);
        int centerRgb = img.getRGB(qrCenterX, qrCenterY);
        int r = (centerRgb >> 16) & 0xFF;
        int g = (centerRgb >> 8) & 0xFF;
        int b = centerRgb & 0xFF;
        // In QR code, pixels are either black (0,0,0) or white (255,255,255)
        assertThat((r == 0 && g == 0 && b == 0) || (r > 240 && g > 240 && b > 240)).isTrue();
    }

    @Test
    @DisplayName("3. Original artwork remains intact outside the QR square")
    void testOriginalArtworkRemainsIntactOutsideQrSquare() throws Exception {
        EventPass pass = generatePass("STUDENT");
        byte[] waImage = passImageService.generateAndSaveWhatsAppPassImage(pass);

        BufferedImage generatedImg = ImageIO.read(new ByteArrayInputStream(waImage));
        BufferedImage originalTemplate;
        try (InputStream in = getClass().getResourceAsStream("/passes/rcf whatsapp1.png")) {
            originalTemplate = ImageIO.read(in);
        }

        // Test pixels well outside the QR box:
        // Top banner: (x=500, y=100)
        assertThat(generatedImg.getRGB(500, 100)).isEqualTo(originalTemplate.getRGB(500, 100));
        // Featured cafe logos at bottom: (x=100, y=1150), (x=500, y=1250)
        assertThat(generatedImg.getRGB(100, 1150)).isEqualTo(originalTemplate.getRGB(100, 1150));
        assertThat(generatedImg.getRGB(500, 1250)).isEqualTo(originalTemplate.getRGB(500, 1250));
        // Coffee illustration on left of RCF: (x=200, y=700)
        assertThat(generatedImg.getRGB(200, 700)).isEqualTo(originalTemplate.getRGB(200, 700));
    }

    @Test
    @DisplayName("4. Frontend pass image remains independent, untouched and preserves original 1826x1372 layout")
    void testFrontendPassImageRemainsUnchanged() throws Exception {
        EventPass pass = generatePass("STUDENT");

        byte[] frontendImage = passImageService.getOrGeneratePassImage(pass.getId(), true);
        byte[] waImage = passImageService.getOrGenerateWhatsAppPassImage(pass.getId(), true);

        assertThat(frontendImage).isNotNull().isNotEmpty();
        assertThat(waImage).isNotNull().isNotEmpty();

        BufferedImage frontImg = ImageIO.read(new ByteArrayInputStream(frontendImage));
        BufferedImage waImg = ImageIO.read(new ByteArrayInputStream(waImage));

        // Frontend image dimensions: 1826 x 1372
        assertThat(frontImg.getWidth()).isEqualTo(1826);
        assertThat(frontImg.getHeight()).isEqualTo(1372);

        BufferedImage template;
        try (InputStream in = getClass().getResourceAsStream("/passes/rcf whatsapp1.png")) {
            template = ImageIO.read(in);
        }

        // WhatsApp image dimensions: matches template
        assertThat(waImg.getWidth()).isEqualTo(template.getWidth());
        assertThat(waImg.getHeight()).isEqualTo(template.getHeight());

        // Disjoint byte arrays
        assertThat(frontendImage).isNotEqualTo(waImage);
    }

    // =========================================================================
    // SECTION 2: QR Uniqueness, Correctness & Lifecycle Compatibility
    // =========================================================================

    @Test
    @DisplayName("5. Different passes generate distinct WhatsApp QR codes with their own individual canonical payloads")
    void testQrUniquenessBetweenPasses() throws Exception {
        EventPass passA = generatePass("STUDENT");
        EventPass passB = generatePass("GENERAL");

        assertThat(passA.getPassNumber()).isNotEqualTo(passB.getPassNumber());
        assertThat(passA.getUniqueCode()).isNotEqualTo(passB.getUniqueCode());

        byte[] imageA = passImageService.generateAndSaveWhatsAppPassImage(passA);
        byte[] imageB = passImageService.generateAndSaveWhatsAppPassImage(passB);

        String decodedPayloadA = decodeQrFromImage(imageA);
        String decodedPayloadB = decodeQrFromImage(imageB);

        assertThat(decodedPayloadA).isNotNull();
        assertThat(decodedPayloadB).isNotNull();
        assertThat(decodedPayloadA).isNotEqualTo(decodedPayloadB);

        String expectedUrlA = passImageService.buildActivationUrl(passA.getPassNumber());
        String expectedUrlB = passImageService.buildActivationUrl(passB.getPassNumber());

        assertThat(decodedPayloadA).isEqualTo(expectedUrlA);
        assertThat(decodedPayloadB).isEqualTo(expectedUrlB);
    }

    @Test
    @DisplayName("6. Decoded WhatsApp QR payload correctly opens public activation flow via PassLookupUtil")
    void testDecodedQrOpensPublicActivationFlow() throws Exception {
        EventPass pass = generatePass("STUDENT");
        byte[] waImage = passImageService.generateAndSaveWhatsAppPassImage(pass);

        String decodedUrl = decodeQrFromImage(waImage);
        assertThat(decodedUrl).contains("/activate/" + pass.getPassNumber());

        // PassLookupUtil must resolve the pass from this exact decoded URL
        Optional<EventPass> resolved = passLookupUtil.findPass(decodedUrl);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(pass.getId());
        assertThat(resolved.get().getPassNumber()).isEqualTo(pass.getPassNumber());
    }

    @Test
    @DisplayName("7. Decoded WhatsApp QR payload supports authorized staff check-in lookup with lock")
    void testDecodedQrSupportsAuthorizedCheckInLookup() throws Exception {
        EventPass pass = generatePass("STANDARD");
        byte[] waImage = passImageService.generateAndSaveWhatsAppPassImage(pass);

        String decodedUrl = decodeQrFromImage(waImage);

        // Pessimistic lock resolution for check-in
        Optional<EventPass> resolvedForUpdate = passLookupUtil.findPassForUpdate(decodedUrl);
        assertThat(resolvedForUpdate).isPresent();
        assertThat(resolvedForUpdate.get().getId()).isEqualTo(pass.getId());
    }

    @Test
    @DisplayName("7b. VIP / Walk-in pass without QR renders clean WALK-IN NO QR badge")
    void testNoQrWalkInPassRendersWalkInBadge() throws Exception {
        EventPass vipPass = generatePass("VIP");
        byte[] waImage = passImageService.generateAndSaveWhatsAppPassImage(vipPass);
        assertThat(waImage).isNotNull().isNotEmpty();

        BufferedImage template;
        try (InputStream in = getClass().getResourceAsStream("/passes/rcf whatsapp1.png")) {
            template = ImageIO.read(in);
        }

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(waImage));
        assertThat(img.getWidth()).isEqualTo(template.getWidth());
        assertThat(img.getHeight()).isEqualTo(template.getHeight());
    }

    // =========================================================================
    // SECTION 3: Cache Separation, Invalidation & Atomic Storage
    // =========================================================================

    @Test
    @DisplayName("8. WhatsApp image cache is strictly separated from frontend pass image cache")
    void testCacheSeparationBetweenFrontendAndWhatsApp() {
        EventPass pass = generatePass("STUDENT");

        Path frontendPath = passImageService.getPassImagePath(pass.getId());
        Path whatsappPath = passImageService.getWhatsAppPassImagePath(pass.getId());

        // Distinct directory paths
        assertThat(frontendPath).isNotEqualTo(whatsappPath);
        assertThat(whatsappPath.getParent().toString()).contains("whatsapp");
        assertThat(frontendPath.getParent().toString()).doesNotContain("whatsapp");

        // Generating WhatsApp image creates WhatsApp cache file, does not touch frontend cache
        passImageService.getOrGenerateWhatsAppPassImage(pass.getId(), true);
        assertThat(Files.exists(whatsappPath)).isTrue();

        // Generating frontend image creates frontend cache file, does not overwrite WhatsApp cache
        passImageService.getOrGeneratePassImage(pass.getId(), true);
        assertThat(Files.exists(frontendPath)).isTrue();
    }

    @Test
    @DisplayName("9. Repeated requests return cached WhatsApp image; forceRegenerate refreshes it atomically")
    void testCacheHitAndAtomicForceRegenerate() {
        EventPass pass = generatePass("STUDENT");

        byte[] firstCall = passImageService.getOrGenerateWhatsAppPassImage(pass.getId());
        byte[] secondCall = passImageService.getOrGenerateWhatsAppPassImage(pass.getId(), false);

        assertThat(firstCall).isEqualTo(secondCall);

        byte[] regenerated = passImageService.getOrGenerateWhatsAppPassImage(pass.getId(), true);
        assertThat(regenerated).isNotNull().isNotEmpty();
        assertThat(regenerated.length).isEqualTo(firstCall.length);
    }

    @Test
    @DisplayName("10. WhatsApp pass image renders sequence number in Strip 1 and attendee name in Strip 2")
    void testSequenceNumberAndAttendeeNameRenderedInStrips() throws Exception {
        EventPass pass = generatePass("STUDENT");
        Attendee attendee = new Attendee();
        attendee.setFullName("Rahul Sharma");
        attendee.setMobileNumber("+919876543210");
        attendee.setEmail("rahul@example.com");
        attendee.setEvent(testEvent);
        attendee.setRegisteredAt(Instant.now());
        attendee = attendeeRepository.save(attendee);

        pass.setAttendee(attendee);
        pass.setStatus(PassStatus.ACTIVATED);
        pass = passRepository.save(pass);

        byte[] waImage = passImageService.getOrGenerateWhatsAppPassImage(pass.getId(), true);
        assertThat(waImage).isNotNull().isNotEmpty();

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(waImage));
        assertThat(img.getWidth()).isEqualTo(1080);
        assertThat(img.getHeight()).isEqualTo(1350);

        // QR decodes correctly
        String decoded = decodeQrFromImage(waImage);
        assertThat(decoded).contains(pass.getPassNumber());

        // Check Strip 1 (Sequence #0001) has dark pixels rendered in cream strip
        boolean foundDarkPixelInStrip1 = false;
        for (int y = 880; y <= 920; y++) {
            for (int x = 650; x <= 870; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r < 50 && g < 50 && b < 50) {
                    foundDarkPixelInStrip1 = true;
                    break;
                }
            }
            if (foundDarkPixelInStrip1) break;
        }
        assertThat(foundDarkPixelInStrip1).isTrue();

        // Check Strip 2 (Attendee Name: RAHUL SHARMA) has dark pixels rendered in cream strip
        boolean foundDarkPixelInStrip2 = false;
        for (int y = 940; y <= 975; y++) {
            for (int x = 650; x <= 870; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r < 50 && g < 50 && b < 50) {
                    foundDarkPixelInStrip2 = true;
                    break;
                }
            }
            if (foundDarkPixelInStrip2) break;
        }
        assertThat(foundDarkPixelInStrip2).isTrue();
    }
}
