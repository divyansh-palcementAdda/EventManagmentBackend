package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.EmailOutbox;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.EmailOutboxStatus;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.NotificationType;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot;
import com.app.eventmanagmentsystem.repository.AttendeeRepository;
import com.app.eventmanagmentsystem.repository.EmailOutboxRepository;
import com.app.eventmanagmentsystem.repository.EventDayRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.impl.EmailOutboxProcessor;
import com.app.eventmanagmentsystem.service.impl.PassImageService;
import com.app.eventmanagmentsystem.service.impl.RcfEmailPassService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
public class RcfEmailPassIntegrationTest {

    @Autowired
    private RcfEmailPassService rcfEmailPassService;

    @Autowired
    private PassImageService passImageService;

    @Autowired
    private EmailOutboxProcessor emailOutboxProcessor;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventDayRepository eventDayRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        emailOutboxRepository.deleteAll();
        // Return a real MimeMessage when createMimeMessage is called on the mock mailSender
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new JavaMailSenderImpl().createMimeMessage());
    }

    @Test
    @DisplayName("1. Verify RCF email artwork resource exists on classpath and has exact 1080x1350 dimensions")
    void testEmailArtworkResourceExistsAndHasExpectedDimensions() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/passes/rcf mail1.png")) {
            assertThat(in).as("Artwork /passes/rcf mail1.png must be present on classpath").isNotNull();
            BufferedImage img = ImageIO.read(in);
            assertThat(img).isNotNull();
            assertThat(img.getWidth()).isEqualTo(1080);
            assertThat(img.getHeight()).isEqualTo(1350);
        }
    }

    @Test
    @DisplayName("2. Verify HTML template exists on classpath and substitutes all dynamic placeholders")
    void testHtmlTemplateLoadsAndSubstitutesPlaceholders() {
        PassImageSnapshot snapshot = PassImageSnapshot.builder()
                .passId(UUID.randomUUID())
                .passNumber("RCF26-TU445NPP-0001")
                .sequenceNumber(1L)
                .category("HQ STAFF")
                .attendeeName("Rohan Sharma")
                .eventName("Renaissance Coffee Festival 2026")
                .eventVenue("Renaissance University, Indore")
                .validDates(List.of(LocalDate.of(2026, 12, 11), LocalDate.of(2026, 12, 12)))
                .build();

        String activationUrl = "https://event.areyoureporting.com/activate/RCF26-TU445NPP-0001";
        String html = rcfEmailPassService.renderEmailHtml(snapshot, activationUrl);

        assertThat(html).isNotNull();
        assertThat(html).contains("Rohan Sharma");
        assertThat(html).contains("RCF26-TU445NPP-0001");
        assertThat(html).contains("#0001");
        assertThat(html).contains("HQ STAFF");
        assertThat(html).contains("Renaissance University, Indore");
        assertThat(html).contains("cid:rcfPassImage");
        assertThat(html).contains(activationUrl);

        // Verify placeholders are fully replaced
        assertThat(html).doesNotContain("{{ATTENDEE_NAME}}");
        assertThat(html).doesNotContain("{{PASS_NUMBER}}");
        assertThat(html).doesNotContain("{{SEQUENCE_NUMBER}}");
        assertThat(html).doesNotContain("{{CATEGORY}}");
        assertThat(html).doesNotContain("{{EVENT_NAME}}");
        assertThat(html).doesNotContain("{{VENUE}}");
    }

    @Test
    @DisplayName("3. Verify generated RCF email pass image contains dynamic QR code and correct sequence number")
    void testRenderRcfEmailPassImageContainsDynamicQrAndSequence() throws Exception {
        PassImageSnapshot snapshot = PassImageSnapshot.builder()
                .passId(UUID.randomUUID())
                .passNumber("RCF26-TU445NPP-0001")
                .sequenceNumber(1L)
                .category("STUDENT")
                .attendeeName("Priya Verma")
                .eventName("Renaissance Coffee Festival")
                .eventVenue("Renaissance University Campus")
                .validDates(List.of(LocalDate.of(2026, 12, 11)))
                .build();

        byte[] imageBytes = rcfEmailPassService.renderRcfEmailPassImage(snapshot);
        assertThat(imageBytes).isNotNull().isNotEmpty();

        BufferedImage composite = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertThat(composite.getWidth()).isEqualTo(1080);
        assertThat(composite.getHeight()).isEqualTo(1350);

        // Decode QR code from the composite image
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(composite)));
        Result qrResult = new MultiFormatReader().decode(bitmap, hints);

        assertThat(qrResult).isNotNull();
        String expectedPayload = passImageService.buildActivationUrl("RCF26-TU445NPP-0001");
        assertThat(qrResult.getText()).isEqualTo(expectedPayload);
    }

    @Test
    @DisplayName("4. Verify sequence number formatting resolves correctly for varied formats")
    void testSequenceNumberFormatting() {
        // Hyphenated pass number
        PassImageSnapshot snap1 = PassImageSnapshot.builder()
                .passNumber("RCF26-HXGHMEDQ-0002")
                .build();
        assertThat(rcfEmailPassService.resolveSequenceText(snap1)).isEqualTo("#0002");

        // Numeric sequence number fallback
        PassImageSnapshot snap2 = PassImageSnapshot.builder()
                .passNumber("RCF26")
                .sequenceNumber(42L)
                .build();
        assertThat(rcfEmailPassService.resolveSequenceText(snap2)).isEqualTo("#0042");

        // Plain pass number with hash already present
        PassImageSnapshot snap3 = PassImageSnapshot.builder()
                .passNumber("#0005")
                .build();
        assertThat(rcfEmailPassService.resolveSequenceText(snap3)).isEqualTo("#0005");
    }

    @Test
    @DisplayName("5. Verify standalone QR code generator creates valid 350x350 QR code")
    void testStandaloneQrGeneration() throws Exception {
        PassImageSnapshot snapshot = PassImageSnapshot.builder()
                .passId(UUID.randomUUID())
                .passNumber("RCF26-ABCD-0007")
                .build();

        byte[] qrBytes = rcfEmailPassService.generateStandaloneQrPng(snapshot);
        assertThat(qrBytes).isNotNull().isNotEmpty();

        BufferedImage qrImg = ImageIO.read(new ByteArrayInputStream(qrBytes));
        assertThat(qrImg.getWidth()).isEqualTo(350);
        assertThat(qrImg.getHeight()).isEqualTo(350);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qrImg)));
        Result qrResult = new MultiFormatReader().decode(bitmap);
        assertThat(qrResult).isNotNull();
        assertThat(qrResult.getText()).isEqualTo(passImageService.buildActivationUrl("RCF26-ABCD-0007"));
    }

    @Test
    @DisplayName("6. Verify EmailOutboxProcessor dispatches multipart HTML MIME message with inline CID resources for RCF passes")
    @Transactional
    void testMimeMessageDeliveryWithInlineCidForRcf() throws Exception {
        // 1. Create Event
        Event event = new Event();
        event.setName("Renaissance Coffee Festival 2026");
        event.setEventCode("RCF" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        event.setVenue("Renaissance University Campus");
        event.setTimezone("Asia/Kolkata");
        event.setStartsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        event.setEndsAt(Instant.now().plus(2, ChronoUnit.DAYS));
        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.save(event);

        // 2. Create EventDay
        EventDay eventDay = new EventDay();
        eventDay.setEvent(event);
        eventDay.setDayNumber(1);
        eventDay.setLabel("Day 1");
        eventDay.setEventDate(LocalDate.now().plusDays(1));
        eventDay = eventDayRepository.save(eventDay);

        // 3. Create Attendee
        Attendee attendee = new Attendee();
        attendee.setEvent(event);
        attendee.setFullName("Aditya Birla");
        attendee.setEmail("aditya@rcf.example.com");
        attendee.setMobileNumber("+919988776655");
        attendee.setRegisteredAt(Instant.now());
        attendee = attendeeRepository.save(attendee);

        // 4. Create EventPass
        EventPass pass = new EventPass();
        pass.setEvent(event);
        pass.setPassNumber("RCF26-TU445NPP-0001");
        pass.setSequenceNumber(1L);
        pass.setCategory("VIP");
        pass.setStatus(PassStatus.ACTIVATED);
        pass.setAttendee(attendee);
        pass.setValidDays(Set.of(eventDay));
        pass.setGeneratedAt(Instant.now());
        pass = eventPassRepository.save(pass);

        // 5. Create EmailOutbox record
        EmailOutbox outbox = new EmailOutbox();
        outbox.setRecipientEmail("aditya@rcf.example.com");
        outbox.setRecipientName("Aditya Birla");
        outbox.setNotificationType(NotificationType.PASS_ACTIVATION);
        outbox.setReferenceId(pass.getId().toString());
        outbox.setStatus(EmailOutboxStatus.PENDING);
        outbox.setMaxAttempts(5);
        outbox.setNextRetryAt(Instant.now().minusSeconds(10));
        outbox = emailOutboxRepository.save(outbox);

        // 6. Process outbox
        emailOutboxProcessor.processOutbox();

        // 7. Verify MimeMessage was sent
        ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(mimeCaptor.capture());

        MimeMessage sentMessage = mimeCaptor.getValue();
        assertThat(sentMessage).isNotNull();
        sentMessage.saveChanges();
        assertThat(sentMessage.getSubject()).contains("Entry Pass");
        assertThat(sentMessage.getSubject()).contains("RCF26-TU445NPP-0001");

        // Inspect MIME structure recursively
        Object content = sentMessage.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);

        MimeMultipart multipart = (MimeMultipart) content;
        String htmlBody = extractHtmlContent(multipart);
        assertThat(htmlBody).as("Must contain HTML body").isNotNull();
        assertThat(htmlBody).contains("cid:rcfPassImage");
        assertThat(htmlBody).contains("Aditya Birla");
        assertThat(htmlBody).contains("RCF26-TU445NPP-0001");

        boolean foundInlineRcfImage = hasInlineContentId(multipart, "rcfPassImage");
        boolean foundInlineQrImage = hasInlineContentId(multipart, "passQrCode");

        assertThat(foundInlineRcfImage).as("Must contain inline rcfPassImage").isTrue();
        assertThat(foundInlineQrImage).as("Must contain inline passQrCode").isTrue();

        // 8. Verify record status is SENT
        EmailOutbox updated = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(updated.getSentAt()).isNotNull();
    }

    private String extractHtmlContent(Object content) throws Exception {
        if (content instanceof String s) {
            return s;
        } else if (content instanceof MimeMultipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                Object partContent = part.getContent();
                if (partContent instanceof String s) {
                    return s;
                } else if (partContent instanceof MimeMultipart nested) {
                    String extracted = extractHtmlContent(nested);
                    if (extracted != null) return extracted;
                }
            }
        }
        return null;
    }

    private boolean hasInlineContentId(MimeMultipart mp, String targetCid) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart part = mp.getBodyPart(i);
            String[] cids = part.getHeader("Content-ID");
            if (cids != null) {
                for (String c : cids) {
                    if (c.contains(targetCid)) return true;
                }
            }
            if (part.getContent() instanceof MimeMultipart nested) {
                if (hasInlineContentId(nested, targetCid)) return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("7. Verify non-activation notification types (e.g. PASS_REVOCATION) use standard SimpleMailMessage")
    @Transactional
    void testNonActivationNotificationUsesStandardEmailMessage() {
        // 1. Create Non-RCF Event
        Event event = new Event();
        event.setName("Tech Conference 2026");
        event.setEventCode("TECH" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        event.setVenue("Main Auditorium");
        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.save(event);

        // 2. Create Attendee
        Attendee attendee = new Attendee();
        attendee.setEvent(event);
        attendee.setFullName("Sam Altman");
        attendee.setEmail("sam@example.com");
        attendee.setMobileNumber("+919111222333");
        attendee.setRegisteredAt(Instant.now());
        attendee = attendeeRepository.save(attendee);

        // 3. Create EventPass
        EventPass pass = new EventPass();
        pass.setEvent(event);
        pass.setPassNumber("TECH26-TEST-0001");
        pass.setSequenceNumber(1L);
        pass.setCategory("DELEGATE");
        pass.setStatus(PassStatus.REVOKED);
        pass.setAttendee(attendee);
        pass.setGeneratedAt(Instant.now());
        pass = eventPassRepository.save(pass);

        // 4. Create EmailOutbox record
        EmailOutbox outbox = new EmailOutbox();
        outbox.setRecipientEmail("sam@example.com");
        outbox.setRecipientName("Sam Altman");
        outbox.setNotificationType(NotificationType.PASS_REVOCATION);
        outbox.setReferenceId(pass.getId().toString());
        outbox.setStatus(EmailOutboxStatus.PENDING);
        outbox.setMaxAttempts(5);
        outbox.setNextRetryAt(Instant.now().minusSeconds(10));
        outbox = emailOutboxRepository.save(outbox);

        // 5. Process outbox
        emailOutboxProcessor.processOutbox();

        // 6. Verify SimpleMailMessage was sent (not MimeMessage)
        ArgumentCaptor<SimpleMailMessage> simpleCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(simpleCaptor.capture());
        verify(mailSender, never()).send(any(MimeMessage.class));

        SimpleMailMessage sent = simpleCaptor.getValue();
        assertThat(sent.getTo()).contains("sam@example.com");
        assertThat(sent.getSubject()).isEqualTo("Your Event Pass Has Been Revoked");

        // 7. Verify status is SENT
        EmailOutbox updated = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
    }

    @Test
    @DisplayName("8. Verify pass activation for custom/test event (e.g. Test123) dispatches rich MIME email with background artwork and dynamic QR")
    @Transactional
    void testCustomEventPassActivationDispatchesRichMimeEmail() throws Exception {
        // 1. Create Event named Test123
        Event event = new Event();
        event.setName("Test123");
        event.setEventCode("TEST123");
        event.setVenue("Renaissance University Campus");
        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.save(event);

        // 2. Create Attendee
        Attendee attendee = new Attendee();
        attendee.setEvent(event);
        attendee.setFullName("Divyansh Singh Tomar");
        attendee.setEmail("divyansh@example.com");
        attendee.setMobileNumber("+919876543210");
        attendee.setRegisteredAt(Instant.now());
        attendee = attendeeRepository.save(attendee);

        // 3. Create EventPass
        EventPass pass = new EventPass();
        pass.setEvent(event);
        pass.setPassNumber("TEST123-ZO7PBP1T-0006");
        pass.setSequenceNumber(6L);
        pass.setCategory("STUDENT");
        pass.setStatus(PassStatus.ACTIVATED);
        pass.setAttendee(attendee);
        pass.setGeneratedAt(Instant.now());
        pass = eventPassRepository.save(pass);

        // 4. Create EmailOutbox record
        EmailOutbox outbox = new EmailOutbox();
        outbox.setRecipientEmail("divyansh@example.com");
        outbox.setRecipientName("Divyansh Singh Tomar");
        outbox.setNotificationType(NotificationType.PASS_ACTIVATION);
        outbox.setReferenceId(pass.getId().toString());
        outbox.setPayload("{\"passNumber\":\"TEST123-ZO7PBP1T-0006\",\"category\":\"STUDENT\",\"eventId\":\"" + event.getId() + "\",\"eventName\":\"Test123\",\"attendeeName\":\"Divyansh Singh Tomar\"}");
        outbox.setStatus(EmailOutboxStatus.PENDING);
        outbox.setMaxAttempts(5);
        outbox.setNextRetryAt(Instant.now().minusSeconds(10));
        outbox = emailOutboxRepository.save(outbox);

        // 5. Process outbox
        emailOutboxProcessor.processOutbox();

        // 6. Verify MimeMessage was sent
        ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(mimeCaptor.capture());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        MimeMessage sentMessage = mimeCaptor.getValue();
        assertThat(sentMessage).isNotNull();
        sentMessage.saveChanges();
        assertThat(sentMessage.getSubject()).contains("Your Test123 Entry Pass — TEST123-ZO7PBP1T-0006");

        String htmlBody = extractHtmlContent(sentMessage.getContent());
        assertThat(htmlBody).contains("Divyansh Singh Tomar");
        assertThat(htmlBody).contains("TEST123-ZO7PBP1T-0006");
        assertThat(htmlBody).contains("#0006");
        assertThat(htmlBody).contains("cid:rcfPassImage");

        // 7. Verify status is SENT
        EmailOutbox updated = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
    }
}
