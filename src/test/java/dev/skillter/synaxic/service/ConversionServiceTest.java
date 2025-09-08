package dev.skillter.synaxic.service;

import dev.skillter.synaxic.exception.ConversionException;
import dev.skillter.synaxic.model.dto.ByteConversionResponse;
import dev.skillter.synaxic.model.dto.ColorConversionResponse;
import dev.skillter.synaxic.model.dto.ContrastRatioResponse;
import dev.skillter.synaxic.model.dto.UnitConversionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ConversionServiceTest {

    private ConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new ConversionService();
    }

    @Test
    void convertUnits_shouldSucceedForValidConversion() {
        UnitConversionResponse response = conversionService.convertUnits("mi", "km", 10);
        assertThat(response.result()).isCloseTo(16.0934, within(0.001));
    }

    @Test
    void convertUnits_shouldThrowExceptionForIncompatibleUnits() {
        assertThatThrownBy(() -> conversionService.convertUnits("kg", "m", 10))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("Incompatible units for conversion: kg to m");
    }

    @Test
    void convertUnits_shouldThrowExceptionForInvalidUnit() {
        assertThatThrownBy(() -> conversionService.convertUnits("invalidUnit", "km", 10))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("Invalid unit format provided for 'invalidUnit' or 'km'.");
    }

    @ParameterizedTest
    @CsvSource({
            "MiB, MB, 1, 1.048576",
            "GB, GiB, 1, 0.93132257",
            "kB, B, 15, 15000",
            "TB, TiB, 2, 1.8189894"
    })
    void convertBytes_shouldSucceedForValidConversions(String from, String to, String value, double expected) {
        ByteConversionResponse response = conversionService.convertBytes(from, to, new BigDecimal(value));
        assertThat(response.result().doubleValue()).isCloseTo(expected, within(0.00001));
    }

    @Test
    void convertBytes_shouldThrowExceptionForInvalidUnit() {
        assertThatThrownBy(() -> conversionService.convertBytes("megabyte", "MB", BigDecimal.ONE))
                .isInstanceOf(ConversionException.class)
                .hasMessage("Invalid 'from' byte unit: megabyte");
    }

    @Test
    void convertColor_fromHexToAll() {
        ColorConversionResponse response = conversionService.convertColor("hex", "all", "#FFC107");
        assertThat(response.hex()).isEqualTo("#ffc107");
        assertThat(response.rgb()).isEqualTo("rgb(255, 193, 7)");
        assertThat(response.hsl()).isEqualTo("hsl(45, 100%, 51%)");
    }

    @Test
    void convertColor_fromRgbToAll() {
        ColorConversionResponse response = conversionService.convertColor("rgb", "all", "rgb(33, 150, 243)");
        assertThat(response.hex()).isEqualTo("#2196f3");
        assertThat(response.rgb()).isEqualTo("rgb(33, 150, 243)");
        assertThat(response.hsl()).isEqualTo("hsl(207, 90%, 54%)");
    }

    @Test
    void convertColor_fromHslToAll() {
        ColorConversionResponse response = conversionService.convertColor("hsl", "all", "hsl(348, 83%, 47%)");
        assertThat(response.hex()).isEqualTo("#db143c");
        assertThat(response.rgb()).isEqualTo("rgb(219, 20, 60)");
        assertThat(response.hsl()).isEqualTo("hsl(348, 83%, 47%)");
    }

    @Test
    void convertColor_shouldHandle3DigitHex() {
        ColorConversionResponse response = conversionService.convertColor("hex", "all", "#f0c");
        assertThat(response.hex()).isEqualTo("#ff00cc");
    }

    @Test
    void convertColor_shouldThrowExceptionForInvalidFormat() {
        assertThatThrownBy(() -> conversionService.convertColor("hex", "all", "#12345"))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("Invalid HEX color");
    }

    @Test
    void calculateContrastRatio_blackOnWhite() {
        ContrastRatioResponse response = conversionService.calculateContrastRatio("#000000", "#FFFFFF");
        assertThat(response.ratio()).isEqualTo(21.0);
        assertThat(response.aa()).isTrue();
        assertThat(response.aaa()).isTrue();
    }

    @Test
    void calculateContrastRatio_blueOnGray_shouldFailAll() {
        ContrastRatioResponse response = conversionService.calculateContrastRatio("#0000FF", "#808080");
        assertThat(response.ratio()).isCloseTo(2.18, within(0.01));
        assertThat(response.aa()).isFalse();
        assertThat(response.aaa()).isFalse();
    }

    @Test
    void calculateContrastRatio_blueOnRed_shouldFailAll() {
        ContrastRatioResponse response = conversionService.calculateContrastRatio("#FF0000", "rgb(0, 0, 255)");
        assertThat(response.ratio()).isCloseTo(2.15, within(0.01));
        assertThat(response.aa()).isFalse();
        assertThat(response.aaa()).isFalse();
    }
}