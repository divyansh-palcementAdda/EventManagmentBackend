package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.WhatsAppOutbox;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.WhatsAppOutboxRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassImageService {

    private final EventPassRepository passRepository;
    private final WhatsAppOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PassImageDataLoader passImageDataLoader;

    @Value("${app.upload.dir:uploads}")
    private String baseUploadDir;

    @Value("${app.frontend.url:${app.botsense.media-public-base-url:https://event.areyoureporting.com}}")
    private String frontendBaseUrl;

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getFrontendBaseUrl() {
        return this.frontendBaseUrl;
    }

    public String buildActivationUrl(String passNumberOrId) {
        String base = (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                ? frontendBaseUrl.trim()
                : "https://event.areyoureporting.com";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String identifier = (passNumberOrId != null && !passNumberOrId.isBlank())
                ? passNumberOrId.trim()
                : "EVENT-PASS";
        return base + "/activate/" + URLEncoder.encode(identifier, StandardCharsets.UTF_8);
    }

    private Path getPassUploadDir() {
        Path path = Paths.get(baseUploadDir, "passes");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            log.error("Failed to create pass image directory: {}", e.getMessage());
        }
        return path;
    }

    public Path getPassImagePath(UUID passId) {
        return getPassUploadDir().resolve(passId.toString() + ".png");
    }

    public boolean hasPassImage(UUID passId) {
        if (passId == null) return false;
        Path file = getPassImagePath(passId);
        return Files.exists(file) && Files.isRegularFile(file);
    }

    public byte[] getPassImage(UUID passId) {
        if (passId == null) return null;
        Path file = getPassImagePath(passId);
        if (Files.exists(file)) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                log.error("Failed to read pass image for {}: {}", passId, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Save client-uploaded pass image (PNG/JPEG)
     */
    public String savePassImage(UUID passId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Pass image file cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image uploads are accepted (PNG/JPEG)");
        }

        try {
            byte[] bytes = file.getBytes();
            return savePassImage(passId, bytes);
        } catch (IOException e) {
            log.error("Failed to read uploaded pass image file: {}", e.getMessage());
            throw new RuntimeException("Could not save pass image: " + e.getMessage(), e);
        }
    }

    /**
     * Save binary bytes to passes directory and update pending WhatsApp outbox record
     */
    public String savePassImage(UUID passId, byte[] imageBytes) {
        Path target = getPassImagePath(passId);
        try {
            Files.write(target, imageBytes);
            log.info("Saved pass image for pass {} at {} ({} bytes)", passId, target, imageBytes.length);

            // Update outbox item payload if one is waiting
            notifyOutboxOfImage(passId, target.toString());

            return target.toString();
        } catch (IOException e) {
            log.error("Failed to save pass image for {}: {}", passId, e.getMessage());
            throw new RuntimeException("Could not write pass image: " + e.getMessage(), e);
        }
    }

    private void notifyOutboxOfImage(UUID passId, String imagePath) {
        try {
            List<WhatsAppOutbox> items = outboxRepository.findByReferenceIdAndNotificationType(
                    passId.toString(), WhatsAppNotificationType.PASS_ACTIVATION);
            for (WhatsAppOutbox item : items) {
                Map<String, Object> payloadMap = new HashMap<>();
                if (item.getPayload() != null && !item.getPayload().isBlank()) {
                    payloadMap = objectMapper.readValue(item.getPayload(), new TypeReference<>() {});
                }
                payloadMap.put("imagePath", imagePath);
                payloadMap.put("hasImage", true);
                item.setPayload(objectMapper.writeValueAsString(payloadMap));
                outboxRepository.save(item);
                log.info("Updated WhatsApp outbox record {} with pass image path {}", item.getId(), imagePath);
            }
        } catch (Exception e) {
            log.warn("Could not update WhatsApp outbox record with image path for pass {}: {}", passId, e.getMessage());
        }
    }

    /**
     * Retrieve existing pass image, or generate server-side if not yet available
     */
    public byte[] getOrGeneratePassImage(UUID passId) {
        return getOrGeneratePassImage(passId, false);
    }

    /**
     * Retrieve existing pass image, or generate server-side (optionally forcing re-generation)
     */
    public byte[] getOrGeneratePassImage(UUID passId, boolean forceRegenerate) {
        if (!forceRegenerate) {
            byte[] existing = getPassImage(passId);
            if (existing != null && existing.length > 0) {
                return existing;
            }
        }

        PassImageSnapshot snapshot = passImageDataLoader.loadSnapshot(passId);
        return generateAndSavePassImage(snapshot);
    }

    /**
     * Server-side composite generator (Java AWT / ZXing) from entity
     */
    public byte[] generateAndSavePassImage(EventPass pass) {
        if (pass == null) {
            throw new IllegalArgumentException("Pass entity cannot be null");
        }
        PassImageSnapshot snapshot = passImageDataLoader.createSnapshotFromEntity(pass);
        return generateAndSavePassImage(snapshot);
    }

    /**
     * Server-side composite generator from detached immutable snapshot
     */
    public byte[] generateAndSavePassImage(PassImageSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Pass snapshot cannot be null");
        }
        try {
            BufferedImage frontImg = null;
            BufferedImage backImg = null;

            try (InputStream inFront = getClass().getResourceAsStream("/passes/14.png")) {
                if (inFront != null) frontImg = ImageIO.read(inFront);
            }
            try (InputStream inBack = getClass().getResourceAsStream("/passes/final.png")) {
                if (inBack != null) backImg = ImageIO.read(inBack);
            }

            if (frontImg == null || backImg == null) {
                log.warn("Pass background templates (/passes/14.png or final.png) not found on classpath, creating fallback badge image.");
                return generateFallbackBadgeImage(snapshot);
            }

            int width = frontImg.getWidth(); // 1826
            int frontHeight = frontImg.getHeight(); // 686
            int backHeight = backImg.getHeight(); // 686
            int totalHeight = frontHeight + backHeight; // 1372

            BufferedImage composite = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = composite.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 1. Draw Front Pass
            g2d.drawImage(frontImg, 0, 0, null);

            // 2. Clear baked-in text area below brown pill to prevent overlapping
            Color creamBg = new Color(0xFE, 0xF3, 0xE7);
            g2d.setColor(creamBg);
            int clearX = 690;
            int clearY = 390;
            int clearW = 780;
            int clearH = 255; // 390 + 255 = 645 (comfortably above bottom border at 653)
            g2d.fillRect(clearX, clearY, clearW, clearH);

            // 3. Subtle aesthetic separator line
            g2d.setColor(new Color(0xD8, 0xC8, 0xB8));
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawLine(720, 405, 1420, 405);

            // 4. Dynamic Text Layout Engine
            int contentStartX = 720;
            int maxContentWidth = 700;
            int currentY = 435;

            // Extract event info safely from snapshot
            String dateLabel = buildDateLabel(snapshot.getValidDates(), snapshot.getEventStartsAt(), snapshot.getEventEndsAt(), snapshot.getTimezone());
            String venue = snapshot.getEventVenue();

            // --- SECTION A: EVENT DATES ---
            if (dateLabel != null && !dateLabel.isBlank()) {
                String datePrefix = "EVENT DATES: ";
                int dateFontSize = 22;
                Font prefixFont = new Font("SansSerif", Font.BOLD, dateFontSize);
                Font valueFont = new Font("SansSerif", Font.BOLD, dateFontSize);

                g2d.setFont(prefixFont);
                FontMetrics fmPrefix = g2d.getFontMetrics();
                g2d.setFont(valueFont);
                FontMetrics fmVal = g2d.getFontMetrics();

                while (dateFontSize > 16 && (fmPrefix.stringWidth(datePrefix) + fmVal.stringWidth(dateLabel)) > maxContentWidth) {
                    dateFontSize -= 1;
                    prefixFont = new Font("SansSerif", Font.BOLD, dateFontSize);
                    valueFont = new Font("SansSerif", Font.BOLD, dateFontSize);
                    g2d.setFont(prefixFont);
                    fmPrefix = g2d.getFontMetrics();
                    g2d.setFont(valueFont);
                    fmVal = g2d.getFontMetrics();
                }

                g2d.setFont(prefixFont);
                g2d.setColor(new Color(0x6F, 0x3B, 0x25)); // Rich coffee brown
                g2d.drawString(datePrefix, contentStartX, currentY);

                g2d.setFont(valueFont);
                g2d.setColor(new Color(0x12, 0x12, 0x12)); // Charcoal black
                g2d.drawString(dateLabel, contentStartX + fmPrefix.stringWidth(datePrefix), currentY);

                currentY += 40;
            }

            // --- SECTION B: VENUE & LOCATION ---
            String effectiveVenue = (venue != null && !venue.isBlank()) ? venue.trim() : "RENAISSANCE UNIVERSITY";
            String effectiveLocation = null;
            if (effectiveVenue.contains(",")) {
                String[] parts = effectiveVenue.split(",", 2);
                effectiveVenue = parts[0].trim();
                effectiveLocation = parts[1].trim();
            } else if (effectiveVenue.equalsIgnoreCase("RENAISSANCE UNIVERSITY")) {
                effectiveLocation = "CAMPUS, INDORE";
            }

            String venuePrefix = "VENUE: ";
            int venueFontSize = 23;
            Font venuePrefixFont = new Font("SansSerif", Font.BOLD, venueFontSize);
            Font venueValueFont = new Font("SansSerif", Font.BOLD, venueFontSize);

            g2d.setFont(venuePrefixFont);
            FontMetrics fmVPrefix = g2d.getFontMetrics();
            int prefixW = fmVPrefix.stringWidth(venuePrefix);
            int remainingWidth = maxContentWidth - prefixW;

            // Dynamically scale down venue font if extremely long
            g2d.setFont(venueValueFont);
            List<String> venueLines = wrapText(effectiveVenue.toUpperCase(), venueValueFont, remainingWidth, g2d);
            while (venueLines.size() > 2 && venueFontSize > 18) {
                venueFontSize -= 1;
                venuePrefixFont = new Font("SansSerif", Font.BOLD, venueFontSize);
                venueValueFont = new Font("SansSerif", Font.BOLD, venueFontSize);
                g2d.setFont(venuePrefixFont);
                fmVPrefix = g2d.getFontMetrics();
                prefixW = fmVPrefix.stringWidth(venuePrefix);
                remainingWidth = maxContentWidth - prefixW;
                g2d.setFont(venueValueFont);
                venueLines = wrapText(effectiveVenue.toUpperCase(), venueValueFont, remainingWidth, g2d);
            }

            g2d.setFont(venuePrefixFont);
            g2d.setColor(new Color(0x6F, 0x3B, 0x25));
            g2d.drawString(venuePrefix, contentStartX, currentY);

            g2d.setFont(venueValueFont);
            g2d.setColor(new Color(0x12, 0x12, 0x12));
            if (!venueLines.isEmpty()) {
                g2d.drawString(venueLines.get(0), contentStartX + prefixW, currentY);
            }
            for (int i = 1; i < venueLines.size(); i++) {
                currentY += 30;
                g2d.drawString(venueLines.get(i), contentStartX + prefixW, currentY);
            }

            currentY += 32;

            // --- SECTION C: CAMPUS / LOCATION DETAILS ---
            if (effectiveLocation != null && !effectiveLocation.isBlank()) {
                int locFontSize = 20;
                Font locFont = new Font("SansSerif", Font.BOLD, locFontSize);
                List<String> locLines = wrapText(effectiveLocation.toUpperCase(), locFont, remainingWidth, g2d);

                g2d.setFont(locFont);
                g2d.setColor(new Color(0x4A, 0x35, 0x25)); // Muted location brown
                for (String lLine : locLines) {
                    g2d.drawString(lLine, contentStartX + prefixW, currentY);
                    currentY += 26;
                }
            }

            // 5. Draw Back Pass
            int backY = frontHeight;
            g2d.drawImage(backImg, 0, backY, null);

            // 6. QR Code or No-QR Badge
            boolean isNoQr = snapshot.isNoQr();
            int qrBoxWidth = (int) (width * 0.15); // ~274px
            int qrBoxHeight = qrBoxWidth;
            int qrBoxX = (int) (width - (width * 0.095) - qrBoxWidth);
            int qrBoxY = backY + (int) (backHeight * 0.20);

            if (isNoQr) {
                // Draw amber badge box
                g2d.setColor(new Color(0xfe, 0xf3, 0xc7));
                g2d.fillRoundRect(qrBoxX, qrBoxY, qrBoxWidth, qrBoxHeight, 20, 20);
                g2d.setColor(new Color(0xf5, 0x9e, 0x0b));
                g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{12, 12}, 0));
                g2d.drawRoundRect(qrBoxX, qrBoxY, qrBoxWidth, qrBoxHeight, 20, 20);

                g2d.setColor(new Color(0x92, 0x40, 0x0e));
                g2d.setFont(new Font("SansSerif", Font.BOLD, 36));
                FontMetrics fm = g2d.getFontMetrics();
                String text1 = "BADGE";
                g2d.drawString(text1, qrBoxX + (qrBoxWidth - fm.stringWidth(text1)) / 2, qrBoxY + (qrBoxHeight / 2) - 10);

                g2d.setColor(new Color(0xb4, 0x53, 0x09));
                g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
                fm = g2d.getFontMetrics();
                String text2 = "NO QR";
                g2d.drawString(text2, qrBoxX + (qrBoxWidth - fm.stringWidth(text2)) / 2, qrBoxY + (qrBoxHeight / 2) + 30);
            } else {
                // Generate ZXing QR with full canonical public activation URL
                String identifier = snapshot.getPassNumber() != null ? snapshot.getPassNumber() : snapshot.getPassId().toString();
                String qrData = buildActivationUrl(identifier);
                BufferedImage qrImage = generateQrCodeImage(qrData, qrBoxWidth, qrBoxHeight);
                if (qrImage != null) {
                    g2d.drawImage(qrImage, qrBoxX, qrBoxY, qrBoxWidth, qrBoxHeight, null);
                }
            }

            // 4. Category Badge (upper brown strip)
            int catBoxWidth = (int) (width * 0.22);
            int catBoxHeight = (int) (backHeight * 0.09);
            int catBoxX = (int) (width - (width * 0.055) - catBoxWidth);
            int catBoxY = backY + (int) (backHeight * (1.0 - 0.26 - 0.09));

            String categoryText = (snapshot.getCategory() != null ? snapshot.getCategory() : "STUDENT").toUpperCase();
            g2d.setFont(new Font("SansSerif", Font.BOLD, 34));
            FontMetrics fm = g2d.getFontMetrics();
            int catTextX = catBoxX + (catBoxWidth - fm.stringWidth(categoryText)) / 2;
            int catTextY = catBoxY + ((catBoxHeight - fm.getHeight()) / 2) + fm.getAscent();

            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(categoryText, catTextX + 2, catTextY + 2);
            g2d.setColor(Color.WHITE);
            g2d.drawString(categoryText, catTextX, catTextY);

            // 5. Serial Number Badge (lower brown strip)
            int serialBoxWidth = catBoxWidth;
            int serialBoxHeight = catBoxHeight;
            int serialBoxX = catBoxX;
            int serialBoxY = backY + (int) (backHeight * (1.0 - 0.17 - 0.09));

            String seqNumStr = String.format("%04d", snapshot.getSequenceNumber() != null ? snapshot.getSequenceNumber() : 1);
            String serialText = "#" + seqNumStr;
            g2d.setFont(new Font("SansSerif", Font.BOLD, 32));
            fm = g2d.getFontMetrics();
            int serialTextX = serialBoxX + (serialBoxWidth - fm.stringWidth(serialText)) / 2;
            int serialTextY = serialBoxY + ((serialBoxHeight - fm.getHeight()) / 2) + fm.getAscent();

            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(serialText, serialTextX + 2, serialTextY + 2);
            g2d.setColor(Color.WHITE);
            g2d.drawString(serialText, serialTextX, serialTextY);

            // 6. Rotated sequence number on left strip
            AffineTransform orig = g2d.getTransform();
            g2d.setColor(new Color(0x12, 0x12, 0x12));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
            fm = g2d.getFontMetrics();
            int leftX = (int) (width * 0.03);
            int leftY = backY + (int) (backHeight * 0.70);
            g2d.translate(leftX, leftY);
            g2d.rotate(-Math.PI / 2);
            g2d.drawString(serialText, 0, 0);
            g2d.setTransform(orig);

            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(composite, "PNG", baos);
            byte[] bytes = baos.toByteArray();

            // Save to disk
            savePassImage(snapshot.getPassId(), bytes);
            return bytes;

        } catch (Exception e) {
            log.error("Failed to generate server-side pass image for {}: {}", snapshot.getPassId(), e.getMessage(), e);
            return generateFallbackBadgeImage(snapshot);
        }
    }

    public byte[] generateFallbackBadgeImage(EventPass pass) {
        if (pass == null) return new byte[0];
        PassImageSnapshot snapshot = passImageDataLoader.createSnapshotFromEntity(pass);
        return generateFallbackBadgeImage(snapshot);
    }

    public byte[] generateFallbackBadgeImage(PassImageSnapshot snapshot) {
        if (snapshot == null) return new byte[0];
        try {
            int w = 1200;
            int h = 700;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g.setColor(new Color(0x0f, 0x17, 0x2a));
            g.fillRect(0, 0, w, h);

            // Header Banner
            g.setColor(new Color(0x43, 0x38, 0xca));
            g.fillRect(0, 0, w, 140);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 42));
            String eventName = snapshot.getEventName() != null ? snapshot.getEventName() : "Event Pass";
            g.drawString(eventName, 50, 85);

            // Pass Details
            g.setColor(new Color(0xe2, 0xe8, 0xf0));
            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            g.drawString("Pass Number: " + (snapshot.getPassNumber() != null ? snapshot.getPassNumber() : ""), 50, 240);
            g.drawString("Category: " + (snapshot.getCategory() != null ? snapshot.getCategory() : "STUDENT"), 50, 310);
            if (snapshot.getAttendeeName() != null && !snapshot.getAttendeeName().isBlank()) {
                g.drawString("Attendee: " + snapshot.getAttendeeName(), 50, 380);
            }

            // QR Code with full canonical public activation URL
            String identifier = snapshot.getPassNumber() != null ? snapshot.getPassNumber() : snapshot.getPassId().toString();
            String qrData = buildActivationUrl(identifier);
            BufferedImage qr = generateQrCodeImage(qrData, 240, 240);
            if (qr != null) {
                g.drawImage(qr, w - 300, 200, 240, 240, null);
            }

            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            byte[] bytes = baos.toByteArray();
            savePassImage(snapshot.getPassId(), bytes);
            return bytes;
        } catch (Exception e) {
            log.error("Failed to generate fallback badge image: {}", e.getMessage());
            return new byte[0];
        }
    }

    private BufferedImage generateQrCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            log.error("Could not generate QR code for {}: {}", text, e.getMessage());
            return null;
        }
    }

    private boolean isNoQrPass(EventPass pass) {
        if (pass == null) return false;
        String cat = (pass.getCategory() != null ? pass.getCategory() : "").toUpperCase();
        return cat.contains("VIP") || cat.contains("NO_QR");
    }

    public String buildDateLabel(List<LocalDate> sortedDates, java.time.Instant startsAt, java.time.Instant endsAt, String timezone) {
        if (sortedDates != null && !sortedDates.isEmpty()) {
            return formatDateList(sortedDates);
        }

        if (startsAt != null) {
            String tz = timezone != null && !timezone.isBlank() ? timezone : "UTC";
            java.time.ZoneId zone;
            try {
                zone = java.time.ZoneId.of(tz);
            } catch (Exception e) {
                zone = java.time.ZoneId.of("UTC");
            }
            LocalDate start = startsAt.atZone(zone).toLocalDate();
            if (endsAt != null) {
                LocalDate end = endsAt.atZone(zone).toLocalDate();
                if (!start.equals(end)) {
                    if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
                        return String.format("%s – %s %s, %d",
                                formatOrdinalDay(start.getDayOfMonth()),
                                formatOrdinalDay(end.getDayOfMonth()),
                                start.getMonth().name(),
                                start.getYear());
                    } else if (start.getYear() == end.getYear()) {
                        return String.format("%s %s – %s %s, %d",
                                formatOrdinalDay(start.getDayOfMonth()),
                                start.getMonth().name(),
                                formatOrdinalDay(end.getDayOfMonth()),
                                end.getMonth().name(),
                                start.getYear());
                    } else {
                        return String.format("%s %s, %d – %s %s, %d",
                                formatOrdinalDay(start.getDayOfMonth()),
                                start.getMonth().name(),
                                start.getYear(),
                                formatOrdinalDay(end.getDayOfMonth()),
                                end.getMonth().name(),
                                end.getYear());
                    }
                }
            }
            return String.format("%s %s, %d",
                    formatOrdinalDay(start.getDayOfMonth()),
                    start.getMonth().name(),
                    start.getYear());
        }

        return "14TH – 16TH NOVEMBER, 2026";
    }

    public String buildDateLabel(Set<EventDay> days) {
        return buildDateLabel(days, null);
    }

    public String buildDateLabel(Set<EventDay> days, Event event) {
        List<LocalDate> sortedDates = null;
        if (days != null && !days.isEmpty()) {
            try {
                sortedDates = days.stream()
                        .map(EventDay::getEventDate)
                        .filter(Objects::nonNull)
                        .sorted()
                        .collect(Collectors.toList());
            } catch (Exception ignored) {}
        }
        java.time.Instant starts = null;
        java.time.Instant ends = null;
        String tz = null;
        if (event != null) {
            try {
                starts = event.getStartsAt();
                ends = event.getEndsAt();
                tz = event.getTimezone();
            } catch (Exception ignored) {}
        }
        return buildDateLabel(sortedDates, starts, ends, tz);
    }

    private String formatDateList(List<LocalDate> sortedDates) {
        if (sortedDates.size() == 1) {
            LocalDate d = sortedDates.get(0);
            return String.format("%s %s, %d",
                    formatOrdinalDay(d.getDayOfMonth()),
                    d.getMonth().name(),
                    d.getYear());
        }

        LocalDate first = sortedDates.get(0);
        LocalDate last = sortedDates.get(sortedDates.size() - 1);
        if (first.getMonth() == last.getMonth() && first.getYear() == last.getYear()) {
            return String.format("%s – %s %s, %d",
                    formatOrdinalDay(first.getDayOfMonth()),
                    formatOrdinalDay(last.getDayOfMonth()),
                    first.getMonth().name(),
                    first.getYear());
        } else if (first.getYear() == last.getYear()) {
            return String.format("%s %s – %s %s, %d",
                    formatOrdinalDay(first.getDayOfMonth()),
                    first.getMonth().name(),
                    formatOrdinalDay(last.getDayOfMonth()),
                    last.getMonth().name(),
                    first.getYear());
        } else {
            return String.format("%s %s, %d – %s %s, %d",
                    formatOrdinalDay(first.getDayOfMonth()),
                    first.getMonth().name(),
                    first.getYear(),
                    formatOrdinalDay(last.getDayOfMonth()),
                    last.getMonth().name(),
                    last.getYear());
        }
    }

    private String formatOrdinalDay(int day) {
        if (day >= 11 && day <= 13) {
            return day + "TH";
        }
        return switch (day % 10) {
            case 1 -> day + "ST";
            case 2 -> day + "ND";
            case 3 -> day + "RD";
            default -> day + "TH";
        };
    }

    private List<String> wrapText(String text, Font font, int maxPixelWidth, Graphics2D g2d) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;

        FontMetrics fm = g2d.getFontMetrics(font);
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (fm.stringWidth(testLine) <= maxPixelWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
}
