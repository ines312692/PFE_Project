package com.elyadata.feature.scraper.antibot.captcha.detection;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ImageCaptchaDetector — isRasterImage()")
class ImageCaptchaDetectorTest {

    private static Method isRasterImage;

    @BeforeAll
    static void openMethod() throws Exception {
        isRasterImage = ImageCaptchaDetector.class
                .getDeclaredMethod("isRasterImage", byte[].class);
        isRasterImage.setAccessible(true);
    }

    private boolean check(byte[] bytes) throws Exception {
        return (boolean) isRasterImage.invoke(null, (Object) bytes);
    }

    @Test
    @DisplayName("PNG magic bytes → true")
    void png_recognized() throws Exception {
        byte[] png = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                       0x00, 0x00, 0x00, 0x0D};
        assertThat(check(png)).isTrue();
    }


    @Test
    @DisplayName("JPEG magic bytes (FF D8 FF) → true")
    void jpeg_recognized() throws Exception {
        byte[] jpeg = {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0,
                        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};
        assertThat(check(jpeg)).isTrue();
    }


    @Test
    @DisplayName("BMP magic bytes (42 4D) → true")
    void bmp_recognized() throws Exception {
        byte[] bmp = new byte[12];
        bmp[0] = 0x42; bmp[1] = 0x4D; // "BM"
        assertThat(check(bmp)).isTrue();
    }


    @Test
    @DisplayName("WEBP magic bytes (RIFF....WEBP) → true")
    void webp_recognized() throws Exception {
        byte[] webp = new byte[12];
        webp[0] = 0x52; webp[1] = 0x49; webp[2] = 0x46; webp[3] = 0x46;
        webp[4] = 0x00; webp[5] = 0x00; webp[6] = 0x00; webp[7] = 0x00;
        webp[8] = 0x57; webp[9] = 0x45; webp[10] = 0x42; webp[11] = 0x50;
        assertThat(check(webp)).isTrue();
    }


    @Test
    @DisplayName("GIF magic bytes → false")
    void gif_notRecognized() throws Exception {
        byte[] gif = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00,
                       0x00, 0x00, 0x00, 0x00};
        assertThat(check(gif)).isFalse();
    }

    @Test
    @DisplayName("SVG bytes → false")
    void svg_notRecognized() throws Exception {
        byte[] svg = "<svg xmlns".getBytes();
        byte[] padded = java.util.Arrays.copyOf(svg, 12);
        assertThat(check(padded)).isFalse();
    }

    @Test
    @DisplayName("random bytes → false")
    void random_notRecognized() throws Exception {
        byte[] random = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                          0x08, 0x09, 0x0A, 0x0B};
        assertThat(check(random)).isFalse();
    }

    @Test
    @DisplayName("fewer than 12 bytes → false (too short)")
    void tooShort_false() throws Exception {
        byte[] small = {(byte)0x89, 0x50, 0x4E}; // PNG header but only 3 bytes
        assertThat(check(small)).isFalse();
    }

    @Test
    @DisplayName("empty byte array → false")
    void empty_false() throws Exception {
        assertThat(check(new byte[0])).isFalse();
    }

    @Test
    @DisplayName("exactly 12 bytes of zeros → false")
    void twelvezeros_false() throws Exception {
        assertThat(check(new byte[12])).isFalse();
    }

    @Test
    @DisplayName("RIFF header without WEBP → false")
    void riffWithoutWebp_false() throws Exception {
        byte[] riff = new byte[12];
        riff[0] = 0x52; riff[1] = 0x49; riff[2] = 0x46; riff[3] = 0x46; // RIFF
        riff[8] = 0x41; riff[9] = 0x56; riff[10] = 0x49; riff[11] = 0x20; // AVI
        assertThat(check(riff)).isFalse();
    }
}