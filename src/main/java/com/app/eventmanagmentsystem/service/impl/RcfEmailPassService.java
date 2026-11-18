package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dedicated service for Renaissance Coffee Festival (RCF) Email Pass generation.
 *
 * <p>Uses the original RCF email artwork (passes/rcf mail1.png) as the visual center,
 * overlaying the individual pass QR code and sequence number with high-precision
 * geometry, antialiasing, and email-client resilience.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RcfEmailPassService {

    private final PassImageService passImageService;

    /**
     * Check if a pass belongs to the Renaissance Coffee Festival (RCF) or activation event.
     * All pass activations in this system use the rich email template.
     */
    public boolean isRcfEvent(PassImageSnapshot snapshot) {
        return true;
    }

    /**
     * Resolves the canonical sequence number formatted with leading '#' (e.g. #0001, #0002).
     */
    public String resolveSequenceText(PassImageSnapshot snapshot) {
        if (snapshot == null) return "#0001";
        if (snapshot.getPassNumber() != null && snapshot.getPassNumber().contains("-")) {
            String[] parts = snapshot.getPassNumber().split("-");
            String lastPart = parts[parts.length - 1].trim();
            return lastPart.startsWith("#") ? lastPart : "#" + lastPart;
        } else if (snapshot.getSequenceNumber() != null) {
            return String.format("#%04d", snapshot.getSequenceNumber());
        } else if (snapshot.getPassNumber() != null && !snapshot.getPassNumber().isBlank()) {
            String trimmed = snapshot.getPassNumber().trim();
            return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
        }
        return "#0001";
    }

    /**
     * Renders the complete RCF pass image for email by overlaying the canonical
     * QR code and the sequence number onto the original rcf mail1.png background artwork.
     */
    public byte[] renderRcfEmailPassImage(PassImageSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Pass snapshot cannot be null");
        }
        try {
            BufferedImage templateImg = null;
            try (InputStream in = getClass().getResourceAsStream("/passes/rcf mail1.png")) {
                if (in != null) {
                    templateImg = ImageIO.read(in);
                }
            }

            if (templateImg == null) {
                log.warn("RCF email background template (/passes/rcf mail1.png) not found, falling back to WhatsApp/standard pass.");
                return passImageService.generateAndSaveWhatsAppPassImage(snapshot);
            }

            int width = templateImg.getWidth();   // 1080
            int height = templateImg.getHeight(); // 1350

            BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = composite.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. Draw original background artwork untouched
            g2d.drawImage(templateImg, 0, 0, null);

            // Coordinates scaled relative to 1080 x 1350 baseline
            double scaleX = (double) width / 1080.0;
            double scaleY = (double) height / 1350.0;

            // 2. QR Box: X=558..971 (width 414), Y=705..1115 (height 410), Center=(765, 910)
            int boxX = (int) Math.round(558 * scaleX);
            int boxY = (int) Math.round(705 * scaleY);
            int boxW = (int) Math.round(414 * scaleX);
            int boxH = (int) Math.round(410 * scaleY);
            int cornerRadius = (int) Math.round(24 * scaleX);

            // High-contrast clean white rounded backing for QR code
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Float(boxX, boxY, boxW, boxH, cornerRadius, cornerRadius));

            // Generate canonical QR code
            String identifier = snapshot.getPassNumber() != null ? snapshot.getPassNumber() : snapshot.getPassId().toString();
            String qrData = passImageService.buildActivationUrl(identifier);

            int qrSize = (int) Math.round(350 * scaleX);
            BufferedImage qrImage = generateQrCodeImage(qrData, qrSize, qrSize);
            if (qrImage != null) {
                int qrX = boxX + (boxW - qrSize) / 2;
                int qrY = boxY + (boxH - qrSize) / 2;
                g2d.drawImage(qrImage, qrX, qrY, null);
            }

            // 3. Sequence Number Strip: X=558..971 (width 414), Y=1128..1187 (height 60), Center=(765, 1158)
            int stripX = (int) Math.round(558 * scaleX);
            int stripY = (int) Math.round(1128 * scaleY);
            int stripW = (int) Math.round(414 * scaleX);
            int stripH = (int) Math.round(60 * scaleY);
            int stripRadius = (int) Math.round(24 * scaleX);

            // Clean white rounded backing for sequence strip
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Float(stripX, stripY, stripW, stripH, stripRadius, stripRadius));

            String seqText = resolveSequenceText(snapshot);
            g2d.setColor(new Color(0x12, 0x12, 0x12));
            int seqFontSize = (int) Math.round(32 * scaleX);
            g2d.setFont(new Font("SansSerif", Font.BOLD, seqFontSize));
            FontMetrics fm = g2d.getFontMetrics();

            int textX = stripX + (stripW - fm.stringWidth(seqText)) / 2;
            int textY = stripY + ((stripH - fm.getHeight()) / 2) + fm.getAscent();
            g2d.drawString(seqText, textX, textY);

            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(composite, "PNG", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to render RCF email pass image for {}: {}", snapshot.getPassId(), e.getMessage(), e);
            throw new RuntimeException("Could not render RCF email pass image: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a standalone high-resolution QR PNG for MIME cid:passQrCode embedding.
     */
    public byte[] generateStandaloneQrPng(PassImageSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Pass snapshot cannot be null");
        }
        try {
            String identifier = snapshot.getPassNumber() != null ? snapshot.getPassNumber() : snapshot.getPassId().toString();
            String qrData = passImageService.buildActivationUrl(identifier);
            BufferedImage qr = generateQrCodeImage(qrData, 350, 350);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qr, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate standalone QR code for {}: {}", snapshot.getPassId(), e.getMessage(), e);
            throw new RuntimeException("Could not generate standalone QR code: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the rcf-pass-activation.html template and substitutes all dynamic values.
     */
    public String renderEmailHtml(PassImageSnapshot snapshot, String activationUrl) {
        String template = loadTemplateFromClasspath();

        String attendeeName = snapshot.getAttendeeName() != null && !snapshot.getAttendeeName().isBlank()
                ? snapshot.getAttendeeName() : "Valued Attendee";
        String passNumber = snapshot.getPassNumber() != null ? snapshot.getPassNumber() : "RCF26-PASS";
        String sequenceNumber = resolveSequenceText(snapshot);
        String category = snapshot.getCategory() != null ? snapshot.getCategory().toUpperCase() : "GENERAL";
        String eventName = snapshot.getEventName() != null ? snapshot.getEventName() : "Renaissance Coffee Festival 2026";
        String venue = snapshot.getEventVenue() != null ? snapshot.getEventVenue() : "Renaissance University, Indore";

        String datesStr = formatValidDates(snapshot.getValidDates());

        return template
                .replace("{{ATTENDEE_NAME}}", attendeeName)
                .replace("{{PASS_NUMBER}}", passNumber)
                .replace("{{SEQUENCE_NUMBER}}", sequenceNumber)
                .replace("{{CATEGORY}}", category)
                .replace("{{EVENT_NAME}}", eventName)
                .replace("{{VENUE}}", venue)
                .replace("{{EVENT_DATES}}", datesStr)
                .replace("{{ACTIVATION_URL}}", activationUrl != null ? activationUrl : "#");
    }

    private String formatValidDates(List<LocalDate> validDates) {
        if (validDates == null || validDates.isEmpty()) {
            return "Dec 11th, 12th 2026";
        }
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMM d");
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
        if (validDates.size() == 1) {
            return validDates.get(0).format(fullFormatter);
        }
        String days = validDates.stream()
                .map(d -> d.format(dayFormatter))
                .collect(Collectors.joining(", "));
        return days + " " + validDates.get(validDates.size() - 1).getYear();
    }

    private String loadTemplateFromClasspath() {
        try (InputStream in = getClass().getResourceAsStream("/templates/email/rcf-pass-activation.html")) {
            if (in == null) {
                throw new IllegalStateException("Email template /templates/email/rcf-pass-activation.html not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Could not load email template: {}", e.getMessage(), e);
            throw new RuntimeException("Could not load email template: " + e.getMessage(), e);
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
            log.error("Failed to generate QR code image: {}", e.getMessage(), e);
            throw new RuntimeException("Could not generate QR code: " + e.getMessage(), e);
        }
    }
}
