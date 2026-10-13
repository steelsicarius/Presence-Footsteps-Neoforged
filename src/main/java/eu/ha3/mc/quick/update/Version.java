package eu.ha3.mc.quick.update;

import java.util.Arrays;

/**
 * A mod or minecraft version.
 *
 * @author Sollace
 */
public final class Version implements Comparable<Version> {
    private final String friendlyString;

    private final int[] numbers;
    private final String suffix;

    public Version(String friendlyString) {
        this.friendlyString = friendlyString;

        String[] parts = friendlyString.split("\\.");
        int[] parsed = new int[parts.length];
        int numericParts = 0;

        for (String part : parts) {
            String digits = leadingDigits(part);
            if (digits.isEmpty()) {
                break;
            }
            try {
                parsed[numericParts++] = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                break;
            }
        }

        this.numbers = Arrays.copyOf(parsed, numericParts);
        this.suffix = numericParts == 0 ? friendlyString : friendlyString.substring(lengthOf(this.numbers));
    }

    public static Version parse(String raw) throws VersionParsingException {
        if (raw == null || raw.isBlank()) {
            throw new VersionParsingException("Empty version string");
        }
        return new Version(raw.trim());
    }

    private static String leadingDigits(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return part.substring(0, end);
    }

    private static int lengthOf(int[] numbers) {
        int total = numbers.length;
        for (int number : numbers) {
            if (number > 9) {
                total++;
            }
        }
        return total;
    }

    public String getFriendlyString() {
        return friendlyString;
    }

    @Override
    public int compareTo(Version o) {
        int length = Math.max(numbers.length, o.numbers.length);

        for (int i = 0; i < length; i++) {
            int a = i < numbers.length ? numbers[i] : 0;
            int b = i < o.numbers.length ? o.numbers[i] : 0;

            if (a != b) {
                return Integer.compare(a, b);
            }
        }

        return suffix.compareTo(o.suffix);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version version && friendlyString.equals(version.friendlyString);
    }

    @Override
    public int hashCode() {
        return friendlyString.hashCode();
    }

    @Override
    public String toString() {
        return friendlyString;
    }
}
