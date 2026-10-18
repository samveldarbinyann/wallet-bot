package com.example.walletbot.shared.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class QrCodeService {

    private static final int SIZE = 300;

    public String generateQrDataUrl(String content) {
        byte[] bytes = generateQrCode(content);
        return bytes != null ? "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes) : null;
    }

    public byte[] generateQrCode(String content) {
        BitMatrix matrix = encode(content);
        if (matrix == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("QR PNG generation failed", e);
            return null;
        }
    }

    public byte[] generateQrCodeJpeg(String content) {
        BitMatrix matrix = encode(content);
        if (matrix == null) {
            return null;
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            BufferedImage source = MatrixToImageWriter.toBufferedImage(matrix);
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(source, 0, 0, Color.WHITE, null);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            return out.toByteArray();
        } catch (Exception e) {
            log.error("QR JPEG generation failed", e);
            return null;
        } finally {
            writer.dispose();
        }
    }

    private BitMatrix encode(String content) {
        try {
            QRCodeWriter qrWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8");
            return qrWriter.encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
        } catch (Exception e) {
            log.error("QR encoding failed for: {}", content, e);
            return null;
        }
    }
}
