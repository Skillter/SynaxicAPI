package dev.skillter.synaxic.service;

import dev.skillter.synaxic.exception.ConversionException;
import dev.skillter.synaxic.model.dto.ByteConversionResponse;
import dev.skillter.synaxic.model.dto.ColorConversionResponse;
import dev.skillter.synaxic.model.dto.ContrastRatioResponse;
import dev.skillter.synaxic.model.dto.UnitConversionResponse;
import org.springframework.stereotype.Service;
import tech.units.indriya.format.SimpleUnitFormat;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.format.UnitFormat;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


import static javax.measure.MetricPrefix.KILO;
import static systems.uom.common.USCustomary.*;
import static tech.units.indriya.unit.Units.*;

@Service
public class ConversionService {

    private enum ByteUnit {
        B("B", new BigDecimal("1")),
        KB("kB", new BigDecimal("1000")),
        MB("MB", new BigDecimal("1000000")),
        GB("GB", new BigDecimal("1000000000")),
        TB("TB", new BigDecimal("1000000000000")),
        KiB("KiB", new BigDecimal("1024")),
        MiB("MiB", new BigDecimal("1048576")),
        GiB("GiB", new BigDecimal("1073741824")),
        TiB("TiB", new BigDecimal("1099511627776"));

        private final String symbol;
        private final BigDecimal bytes;

        ByteUnit(String symbol, BigDecimal bytes) {
            this.symbol = symbol;
            this.bytes = bytes;
        }

        public static Optional<ByteUnit> fromSymbol(String symbol) {
            return Stream.of(values())
                    .filter(unit -> unit.symbol.equalsIgnoreCase(symbol))
                    .findFirst();
        }
    }

    private static final Pattern RGB_PATTERN = Pattern.compile("rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)");
    private static final Pattern HSL_PATTERN = Pattern.compile("hsl\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})%?\\s*,\\s*(\\d{1,3})%?\\s*\\)");
    private static final UnitFormat UNIT_FORMAT = SimpleUnitFormat.getInstance();

    static {
        UNIT_FORMAT.label(MILE, "mi");
        UNIT_FORMAT.label(POUND, "lb");
        UNIT_FORMAT.label(FAHRENHEIT, "F");
        UNIT_FORMAT.label(CELSIUS, "C");
        UNIT_FORMAT.label(KILOGRAM, "kg");
        UNIT_FORMAT.label(METRE, "m");
        UNIT_FORMAT.label(KILO(METRE), "km");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public UnitConversionResponse convertUnits(String from, String to, double value) {
        try {
            Unit fromUnit = UNIT_FORMAT.parse(from);
            Unit toUnit = UNIT_FORMAT.parse(to);

            Quantity quantity = Quantities.getQuantity(value, fromUnit);
            double result = quantity.to(toUnit).getValue().doubleValue();

            return new UnitConversionResponse(from, to, value, result);
        } catch (UnconvertibleException e) {
            throw new ConversionException("Incompatible units for conversion: " + from + " to " + to, e);
        } catch (Exception e) {
            throw new ConversionException("Invalid unit format provided for '" + from + "' or '" + to + "'.", e);
        }
    }

    public ByteConversionResponse convertBytes(String from, String to, BigDecimal value) {
        ByteUnit fromUnit = ByteUnit.fromSymbol(from)
                .orElseThrow(() -> new ConversionException("Invalid 'from' byte unit: " + from));
        ByteUnit toUnit = ByteUnit.fromSymbol(to)
                .orElseThrow(() -> new ConversionException("Invalid 'to' byte unit: " + to));

        if (toUnit.bytes.equals(BigDecimal.ZERO)) {
            throw new ConversionException("Cannot divide by zero.");
        }

        BigDecimal ratio = fromUnit.bytes.divide(toUnit.bytes, MathContext.DECIMAL128);
        BigDecimal result = value.multiply(ratio);

        return new ByteConversionResponse(from, to, value, result, ratio);
    }

    public ColorConversionResponse convertColor(String from, String to, String value) {
        Color color = parseColor(from, value);

        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        Map<String, Integer> hslMap = rgbToHsl(r, g, b);
        int h = hslMap.get("h");
        int s = hslMap.get("s");
        int l = hslMap.get("l");

        String hexString = String.format("#%02x%02x%02x", r, g, b);
        String rgbString = String.format("rgb(%d, %d, %d)", r, g, b);
        String hslString = String.format("hsl(%d, %d%%, %d%%)", h, s, l);

        return new ColorConversionResponse(hexString, rgbString, hslString);
    }

    public ContrastRatioResponse calculateContrastRatio(String fg, String bg) {
        Color fgColor = parseColor("auto", fg);
        Color bgColor = parseColor("auto", bg);

        double lum1 = calculateLuminance(fgColor);
        double lum2 = calculateLuminance(bgColor);

        double ratio = (Math.max(lum1, lum2) + 0.05) / (Math.min(lum1, lum2) + 0.05);
        ratio = new BigDecimal(ratio).setScale(2, RoundingMode.HALF_UP).doubleValue();

        boolean aa = ratio >= 4.5;
        boolean aaa = ratio >= 7.0;

        return new ContrastRatioResponse(ratio, aa, aaa);
    }

    private Color parseColor(String format, String value) {
        return switch (format.toLowerCase()) {
            case "hex" -> parseHex(value);
            case "rgb" -> parseRgb(value);
            case "hsl" -> parseHsl(value);
            case "auto" -> {
                if (value.startsWith("#")) yield parseHex(value);
                if (value.toLowerCase().startsWith("rgb")) yield parseRgb(value);
                if (value.toLowerCase().startsWith("hsl")) yield parseHsl(value);
                throw new ConversionException("Could not auto-detect color format for value: " + value);
            }
            default -> throw new ConversionException("Unsupported 'from' color format: " + format);
        };
    }

    private Color parseHex(String hex) {
        String cleanHex = hex.startsWith("#") ? hex.substring(1) : hex;

        if (cleanHex.length() == 3) {
            cleanHex = "" + cleanHex.charAt(0) + cleanHex.charAt(0) +
                    cleanHex.charAt(1) + cleanHex.charAt(1) +
                    cleanHex.charAt(2) + cleanHex.charAt(2);
        }

        if (cleanHex.length() != 6) {
            throw new ConversionException("Invalid HEX color length: " + hex);
        }

        try {
            return new Color(Integer.parseInt(cleanHex, 16));
        } catch (NumberFormatException e) {
            throw new ConversionException("Invalid HEX color value: " + hex, e);
        }
    }

    private Color parseRgb(String rgb) {
        Matcher matcher = RGB_PATTERN.matcher(rgb.toLowerCase());
        if (!matcher.matches()) {
            throw new ConversionException("Invalid RGB format. Expected 'rgb(r, g, b)'.");
        }
        try {
            int r = Integer.parseInt(matcher.group(1));
            int g = Integer.parseInt(matcher.group(2));
            int b = Integer.parseInt(matcher.group(3));
            return new Color(r, g, b);
        } catch (IllegalArgumentException e) {
            throw new ConversionException("RGB values must be between 0 and 255.", e);
        }
    }

    private Color parseHsl(String hsl) {
        Matcher matcher = HSL_PATTERN.matcher(hsl.toLowerCase());
        if (!matcher.matches()) {
            throw new ConversionException("Invalid HSL format. Expected 'hsl(h, s, l)'.");
        }
        try {
            float h = Integer.parseInt(matcher.group(1)) / 360f;
            float s = Integer.parseInt(matcher.group(2)) / 100f;
            float l = Integer.parseInt(matcher.group(3)) / 100f;

            if (s < 0 || s > 1 || l < 0 || l > 1) {
                throw new IllegalArgumentException("HSL values out of range.");
            }

            if (s == 0) {
                int gray = Math.round(l * 255);
                return new Color(gray, gray, gray);
            }

            float q = l < 0.5f ? l * (1 + s) : (l + s) - (l * s);
            float p = 2 * l - q;
            float r = hueToRgb(p, q, h + 1f / 3f);
            float g = hueToRgb(p, q, h);
            float b = hueToRgb(p, q, h - 1f / 3f);

            return new Color(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
        } catch (IllegalArgumentException e) {
            throw new ConversionException("Invalid HSL values.", e);
        }
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private Map<String, Integer> rgbToHsl(int r, int g, int b) {
        float r_norm = r / 255f;
        float g_norm = g / 255f;
        float b_norm = b / 255f;

        float max = Math.max(r_norm, Math.max(g_norm, b_norm));
        float min = Math.min(r_norm, Math.min(g_norm, b_norm));
        float delta = max - min;

        float h = 0, s = 0, l = (max + min) / 2;

        if (delta != 0) {
            s = l > 0.5f ? delta / (2f - max - min) : delta / (max + min);
            if (max == r_norm) {
                h = (g_norm - b_norm) / delta + (g_norm < b_norm ? 6 : 0);
            } else if (max == g_norm) {
                h = (b_norm - r_norm) / delta + 2;
            } else {
                h = (r_norm - g_norm) / delta + 4;
            }
            h /= 6f;
        }

        return Map.of(
                "h", Math.round(h * 360),
                "s", Math.round(s * 100),
                "l", Math.round(l * 100)
        );
    }

    private double calculateLuminance(Color color) {
        double r = linearize(color.getRed() / 255.0);
        double g = linearize(color.getGreen() / 255.0);
        double b = linearize(color.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private double linearize(double value) {
        if (value <= 0.04045) {
            return value / 12.92;
        }
        return Math.pow((value + 0.055) / 1.055, 2.4);
    }
}