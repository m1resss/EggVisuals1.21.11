package egg.client.render.msdf.pipeline;

import lombok.experimental.UtilityClass;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class ColorFormatting {

    public static final Pattern PATTERN = Pattern.compile(
            "\\$\\{(rgba|rgb)\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})(?:,(\\d{1,3}))?\\)}|\\$\\{reset}",
            Pattern.CASE_INSENSITIVE
    );

    private static final HashMap<String, String> FORMATS = new HashMap<>();

    static {
        FORMATS.put("${black}", Formatting.BLACK.toString());
        FORMATS.put("${dark_blue}", Formatting.DARK_BLUE.toString());
        FORMATS.put("${dark_green}", Formatting.DARK_GREEN.toString());
        FORMATS.put("${dark_aqua}", Formatting.DARK_AQUA.toString());
        FORMATS.put("${dark_red}", Formatting.DARK_RED.toString());
        FORMATS.put("${dark_purple}", Formatting.DARK_PURPLE.toString());
        FORMATS.put("${orange}", Formatting.GOLD.toString());
        FORMATS.put("${gray}", Formatting.GRAY.toString());
        FORMATS.put("${dark_gray}", Formatting.DARK_GRAY.toString());
        FORMATS.put("${blue}", Formatting.BLUE.toString());
        FORMATS.put("${green}", Formatting.GREEN.toString());
        FORMATS.put("${aqua}", Formatting.AQUA.toString());
        FORMATS.put("${red}", Formatting.RED.toString());
        FORMATS.put("${purple}", Formatting.LIGHT_PURPLE.toString());
        FORMATS.put("${yellow}", Formatting.YELLOW.toString());
        FORMATS.put("${white}", Formatting.WHITE.toString());
        FORMATS.put("${bold}", Formatting.BOLD.toString());
        FORMATS.put("${reset}", Formatting.RESET.toString());
    }

    public static String get(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        if (input.indexOf("${") < 0) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> format : FORMATS.entrySet()) {
            result = result.replace(format.getKey(), format.getValue());
        }
        return result;
    }

    public static String getColor(int red, int green, int blue) {

        return "${rgb(" + red + ',' + green + ',' + blue + ")}";
    }

    public static String getColor(int red, int green, int blue, int alpha) {
        return "${rgba(" + red + ',' + green + ',' + blue + ',' + alpha + ")}";
    }

    public static String getColor(int color) {
        return getColor(egg.client.render.msdf.pipeline.ColorFormatting.red(color), egg.client.render.msdf.pipeline.ColorFormatting.green(color), egg.client.render.msdf.pipeline.ColorFormatting.blue(color), egg.client.render.msdf.pipeline.ColorFormatting.alpha(color));
    }

    public static Text gradient(String message) {
        MutableText text = Text.empty();
        for (int i = 0; i < message.length(); i++) {
            int fade = egg.client.render.msdf.pipeline.ColorFormatting.fade(i);
            text.append(Text.literal(String.valueOf(message.charAt(i)))
                    .styled(style -> style.withColor(fade)));
        }
        return text;
    }

    public static int overColor(int firstColor, int secondColor, float factorPercent) {
        return egg.client.render.msdf.pipeline.ColorFormatting.overCol(firstColor, secondColor, factorPercent);
    }

    public static String reset() {
        return "${reset}";
    }

    public static String removeFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (text.indexOf("${") < 0) {
            return text;
        }
        return PATTERN.matcher(text).replaceAll("");
    }

    public static String typeRGB() {
        return "rgb";
    }

    public static String typeRGBA() {
        return "rgba";
    }

    public static String replaceColor(String text, int red, int green, int blue) {
        return PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement(getColor(red, green, blue)));
    }

    public static String replaceColor(String text, int red, int green, int blue, int alpha) {
        return PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement(getColor(red, green, blue, alpha)));
    }

    public static ColorTag parseTag(String text, int index, int defaultColor) {
        if (text == null || index < 0 || index >= text.length() - 1) {
            return null;
        }
        if (text.charAt(index) != '$' || text.charAt(index + 1) != '{') {
            return null;
        }

        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.find(index) || matcher.start() != index) {
            return null;
        }

        if (matcher.group().equalsIgnoreCase(reset())) {
            return new ColorTag(defaultColor, matcher.end() - index);
        }

        String type = matcher.group(1).toLowerCase();
        int red = clamp255(Integer.parseInt(matcher.group(2)));
        int green = clamp255(Integer.parseInt(matcher.group(3)));
        int blue = clamp255(Integer.parseInt(matcher.group(4)));
        int alpha = type.equals(typeRGBA()) && matcher.group(5) != null
                ? clamp255(Integer.parseInt(matcher.group(5)))
                : egg.client.render.msdf.pipeline.ColorFormatting.alpha(defaultColor);

        return new ColorTag(packColor(red, green, blue, alpha), matcher.end() - index);
    }

    public static String stripForWidth(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return removeFormatting(get(text));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static int red(int color) { return (color >> 16) & 255; }
    public static int green(int color) { return (color >> 8) & 255; }
    public static int blue(int color) { return color & 255; }
    public static int alpha(int color) { return (color >> 24) & 255; }
    public static int packColor(int r, int g, int b, int a) { return (a << 24) | (r << 16) | (g << 8) | b; }
    public static int fade(int index) { return -1; }
    public static int overCol(int c1, int c2, float factor) { return c1; }

    public record ColorTag(int color, int length) {
    }
}
