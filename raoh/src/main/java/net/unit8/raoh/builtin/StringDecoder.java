package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A decoder for string values with a fluent API for constraints, transforms, and type conversions.
 *
 * <p>Constraints (e.g., {@link #minLength}, {@link #email}) and transforms (e.g., {@link #trim}, {@link #toLowerCase})
 * are chained to produce new decoders. Type conversions (e.g., {@link #uuid}, {@link #iso8601}) return
 * decoders of the converted type.
 *
 * @param <I> the input type
 */
public class StringDecoder<I> implements Decoder<I, String> {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_IP_LENGTH = 45; // max IPv6 with zone id

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]{1,64}@[a-zA-Z0-9.\\-]{1,255}\\.[a-zA-Z]{2,}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$");
    private static final Pattern CUID_PATTERN = Pattern.compile(
            "^c[a-z0-9]{24}$");
    private static final Pattern ULID_PATTERN = Pattern.compile(
            "^[0-9A-HJKMNP-TV-Z]{26}$");

    private final Decoder<I, String> inner;
    private final Decoder<I, String> base;

    public StringDecoder(Decoder<I, String> inner) {
        this.inner = inner;
        this.base = null;
    }

    public StringDecoder(Decoder<I, String> inner, Decoder<I, String> base) {
        this.inner = inner;
        this.base = base;
    }

    @Override
    public Result<String> decode(I in, Path path) {
        return inner.decode(in, path);
    }

    /**
     * Wraps an arbitrary {@link Decoder}{@code <I, String>} as a {@link StringDecoder},
     * enabling the fluent constraint/transform API ({@link #minLength}, {@link #email},
     * {@link #trim}, etc.) on top of any existing string-producing decoder.
     *
     * <p>Use this when you have a custom decoder that already produces a {@code String}
     * and you want to apply further string constraints without building a full decoder from scratch.
     * Factory methods such as {@code net.unit8.raoh.json.JsonDecoders#string()} already return a
     * {@link StringDecoder} directly, so {@code from()} is not needed in those cases.
     *
     * @param <I> the input type
     * @param dec the base decoder to wrap
     * @return a {@link StringDecoder} delegating to {@code dec}
     */
    public static <I> StringDecoder<I> from(Decoder<I, String> dec) {
        return new StringDecoder<>(dec);
    }

    // --- Constraints ---

    /**
     * Requires the string value to contain at least one non-whitespace character.
     *
     * <p>Fails with {@link ErrorCodes#BLANK} when the decoded string is empty or consists
     * entirely of whitespace. Use this after {@link #trim()} to reject strings that become
     * empty after trimming, or stand-alone to reject blank-only input.
     *
     * @return a new decoder that fails with {@link ErrorCodes#BLANK} for blank values
     */
    public StringDecoder<I> nonBlank() {
        return chain((value, path) -> {
            if (value.isBlank()) {
                return Result.fail(path, ErrorCodes.BLANK, "must not be blank");
            }
            return Result.ok(value);
        });
    }

    /**
     * Removes a previously-applied {@link #nonBlank()} constraint, restoring blank-string acceptance.
     *
     * @throws IllegalStateException if this decoder was not created via {@code net.unit8.raoh.json.JsonDecoders#string()}
     *         or {@link net.unit8.raoh.ObjectDecoders#string()} (i.e., no base decoder is available to restore)
     */
    public StringDecoder<I> allowBlank() {
        if (base == null) {
            throw new IllegalStateException(
                    "allowBlank() can only be called on a StringDecoder created by JsonDecoders.string() " +
                    "or ObjectDecoders.string(), which retain the base decoder for restoration.");
        }
        return new StringDecoder<>(base);
    }

    public StringDecoder<I> minLength(int n) {
        return minLength(n, null);
    }

    public StringDecoder<I> minLength(int n, String message) {
        return chain((value, path) -> {
            if (value.length() < n) {
                var meta = Map.<String, Object>of("min", n, "actual", value.length());
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TOO_SHORT, message, meta)
                        : Result.fail(path, ErrorCodes.TOO_SHORT, "must be at least %d characters".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> maxLength(int n) {
        return maxLength(n, null);
    }

    public StringDecoder<I> maxLength(int n, String message) {
        return chain((value, path) -> {
            if (value.length() > n) {
                var meta = Map.<String, Object>of("max", n, "actual", value.length());
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TOO_LONG, message, meta)
                        : Result.fail(path, ErrorCodes.TOO_LONG, "must be at most %d characters".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> fixedLength(int n) {
        return fixedLength(n, null);
    }

    public StringDecoder<I> fixedLength(int n, String message) {
        return chain((value, path) -> {
            if (value.length() != n) {
                var meta = Map.<String, Object>of("expected", n, "actual", value.length());
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_LENGTH, message, meta)
                        : Result.fail(path, ErrorCodes.INVALID_LENGTH, "must be exactly %d characters".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * @param allowed the set of allowed string values
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     */
    public StringDecoder<I> oneOf(String... allowed) {
        var allowedSet = Set.of(allowed);
        var sortedAllowed = List.copyOf(new TreeSet<>(allowedSet));
        var message = "must be one of %s".formatted(sortedAllowed);
        return chain((value, path) -> {
            if (!allowedSet.contains(value)) {
                var meta = Map.<String, Object>of("allowed", sortedAllowed, "actual", value);
                return Result.fail(path, ErrorCodes.NOT_ALLOWED, message, meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> pattern(Pattern p) {
        return pattern(p, ErrorCodes.INVALID_FORMAT, null);
    }

    public StringDecoder<I> pattern(Pattern p, String code) {
        return pattern(p, code, null);
    }

    public StringDecoder<I> pattern(Pattern p, String code, String message) {
        return chain((value, path) -> {
            if (!p.matcher(value).matches()) {
                var meta = Map.<String, Object>of("pattern", p.pattern());
                return message != null
                        ? Result.failCustom(path, code, message, meta)
                        : Result.fail(path, code, "invalid format", meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> startsWith(String prefix) {
        return startsWith(prefix, null);
    }

    public StringDecoder<I> startsWith(String prefix, String message) {
        return chain((value, path) -> {
            if (!value.startsWith(prefix)) {
                var meta = Map.<String, Object>of("prefix", prefix);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, meta)
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "must start with \"%s\"".formatted(prefix), meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> endsWith(String suffix) {
        return endsWith(suffix, null);
    }

    public StringDecoder<I> endsWith(String suffix, String message) {
        return chain((value, path) -> {
            if (!value.endsWith(suffix)) {
                var meta = Map.<String, Object>of("suffix", suffix);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, meta)
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "must end with \"%s\"".formatted(suffix), meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> includes(String substring) {
        return includes(substring, null);
    }

    public StringDecoder<I> includes(String substring, String message) {
        return chain((value, path) -> {
            if (!value.contains(substring)) {
                var meta = Map.<String, Object>of("substring", substring);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, meta)
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "must include \"%s\"".formatted(substring), meta);
            }
            return Result.ok(value);
        });
    }

    // --- Preset constraints ---

    public StringDecoder<I> email() {
        return email(null);
    }

    public StringDecoder<I> email(String message) {
        return chain((value, path) -> {
            if (value.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid email");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> url() {
        return url(null);
    }

    /**
     * Validates that the value is a valid http/https URL.
     * Uses {@link URI} parsing to avoid ReDoS from regex backtracking.
     */
    public StringDecoder<I> url(String message) {
        return chain((value, path) -> {
            if (value.length() > MAX_URL_LENGTH) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid URL");
            }
            try {
                var uri = URI.create(value);
                var scheme = uri.getScheme();
                if ((!"http".equals(scheme) && !"https".equals(scheme))
                        || uri.getHost() == null || uri.getHost().isEmpty()) {
                    return message != null
                            ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                            : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid URL");
                }
            } catch (IllegalArgumentException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid URL");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ipv4() {
        return ipv4(null);
    }

    public StringDecoder<I> ipv4(String message) {
        return chain((value, path) -> {
            if (value.length() > MAX_IP_LENGTH || !IPV4_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid IPv4 address");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ipv6() {
        return ipv6(null);
    }

    /**
     * Validates that the value is a valid IPv6 address.
     * Uses {@link InetAddress#getByName} to avoid ReDoS from complex regex backtracking.
     */
    public StringDecoder<I> ipv6(String message) {
        return chain((value, path) -> {
            if (value.length() > MAX_IP_LENGTH || !isIPv6(value)) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid IPv6 address");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ip() {
        return ip(null);
    }

    public StringDecoder<I> ip(String message) {
        return chain((value, path) -> {
            if (value.length() > MAX_IP_LENGTH
                    || (!IPV4_PATTERN.matcher(value).matches() && !isIPv6(value))) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid IP address");
            }
            return Result.ok(value);
        });
    }

    private static boolean isIPv6(String value) {
        if (!value.contains(":")) return false;
        try {
            // InetAddress.getByName accepts IPv6 literals; strip brackets if present
            var addr = value.startsWith("[") && value.endsWith("]")
                    ? value.substring(1, value.length() - 1) : value;
            var parsed = InetAddress.getByName(addr);
            return parsed instanceof java.net.Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public StringDecoder<I> cuid() {
        return cuid(null);
    }

    public StringDecoder<I> cuid(String message) {
        return chain((value, path) -> {
            if (!CUID_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid CUID");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ulid() {
        return ulid(null);
    }

    public StringDecoder<I> ulid(String message) {
        return chain((value, path) -> {
            if (!ULID_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid ULID");
            }
            return Result.ok(value);
        });
    }

    // --- Transforms ---

    public StringDecoder<I> trim() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(String::trim));
    }

    public StringDecoder<I> toLowerCase() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(String::toLowerCase));
    }

    public StringDecoder<I> toUpperCase() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(String::toUpperCase));
    }

    // --- Type conversions ---

    public Decoder<I, UUID> uuid() {
        return uuid(null);
    }

    public Decoder<I, UUID> uuid(String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid UUID");
            }
        });
    }

    public Decoder<I, URI> uri() {
        return uri(null);
    }

    public Decoder<I, URI> uri(String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(URI.create(value));
            } catch (IllegalArgumentException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid URI");
            }
        });
    }

    /**
     * Parses the string as an ISO 8601 instant (e.g., {@code 2024-01-15T10:30:00Z}).
     *
     * @return a temporal decoder producing {@link Instant}
     */
    public TemporalDecoder<I, Instant> iso8601() {
        return iso8601(null);
    }

    /**
     * Parses the string as an ISO 8601 instant.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link Instant}
     */
    public TemporalDecoder<I, Instant> iso8601(String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(Instant.parse(value));
            } catch (DateTimeParseException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid ISO 8601 instant");
            }
        }));
    }

    /**
     * Parses the string as a local date (e.g., {@code 2024-01-15}).
     *
     * @return a temporal decoder producing {@link LocalDate}
     */
    public TemporalDecoder<I, LocalDate> date() {
        return date(null);
    }

    /**
     * Parses the string as a local date.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link LocalDate}
     */
    public TemporalDecoder<I, LocalDate> date(String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(LocalDate.parse(value));
            } catch (DateTimeParseException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid date (yyyy-MM-dd)");
            }
        }));
    }

    /**
     * Parses the string as a local time (e.g., {@code 10:30:00}).
     *
     * @return a temporal decoder producing {@link LocalTime}
     */
    public TemporalDecoder<I, LocalTime> time() {
        return time(null);
    }

    /**
     * Parses the string as a local time.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link LocalTime}
     */
    public TemporalDecoder<I, LocalTime> time(String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(LocalTime.parse(value));
            } catch (DateTimeParseException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid time (HH:mm:ss)");
            }
        }));
    }

    /**
     * Parses the string as a local date-time (e.g., {@code 2024-01-15T10:30:00}).
     *
     * @return a temporal decoder producing {@link LocalDateTime}
     */
    public TemporalDecoder<I, LocalDateTime> dateTime() {
        return dateTime(null);
    }

    /**
     * Parses the string as a local date-time.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link LocalDateTime}
     */
    public TemporalDecoder<I, LocalDateTime> dateTime(String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(LocalDateTime.parse(value));
            } catch (DateTimeParseException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid ISO-8601 local date-time (e.g., 2024-01-15T10:30 or 2024-01-15T10:30:45)");
            }
        }));
    }

    /**
     * Parses the string as an offset date-time (e.g., {@code 2024-01-15T10:30:00+09:00}).
     *
     * @return a temporal decoder producing {@link OffsetDateTime}
     */
    public TemporalDecoder<I, OffsetDateTime> offsetDateTime() {
        return offsetDateTime(null);
    }

    /**
     * Parses the string as an offset date-time.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link OffsetDateTime}
     */
    public TemporalDecoder<I, OffsetDateTime> offsetDateTime(String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(OffsetDateTime.parse(value));
            } catch (DateTimeParseException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid ISO-8601 offset date-time (e.g., 2024-01-15T10:30:00+09:00)");
            }
        }));
    }

    // --- Numeric / boolean type conversions ---

    /**
     * Parses the string as an integer.
     *
     * <p>Produces a {@link ErrorCodes#TYPE_MISMATCH} error when the string
     * cannot be parsed as an integer. The returned {@link IntDecoder} supports
     * further numeric constraints such as {@code range()}, {@code positive()}, etc.
     *
     * @return an integer decoder with the parsed value
     */
    public IntDecoder<I> toInt() {
        return toInt(null);
    }

    /**
     * Parses the string as an integer.
     *
     * @param message custom error message, or {@code null} for the default
     * @return an integer decoder with the parsed value
     */
    public IntDecoder<I> toInt(String message) {
        return new IntDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TYPE_MISMATCH, message,
                                Map.of("expected", "integer"))
                        : Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected integer",
                                Map.of("expected", "integer"));
            }
        }));
    }

    /**
     * Parses the string as a long integer.
     *
     * <p>Produces a {@link ErrorCodes#TYPE_MISMATCH} error when the string
     * cannot be parsed as a long. The returned {@link LongDecoder} supports
     * further numeric constraints such as {@code range()}, {@code positive()}, etc.
     *
     * @return a long decoder with the parsed value
     */
    public LongDecoder<I> toLong() {
        return toLong(null);
    }

    /**
     * Parses the string as a long integer.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a long decoder with the parsed value
     */
    public LongDecoder<I> toLong(String message) {
        return new LongDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(Long.parseLong(value));
            } catch (NumberFormatException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TYPE_MISMATCH, message,
                                Map.of("expected", "long"))
                        : Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected long",
                                Map.of("expected", "long"));
            }
        }));
    }

    /**
     * Parses the string as a {@link BigDecimal}.
     *
     * <p>Produces a {@link ErrorCodes#TYPE_MISMATCH} error when the string
     * cannot be parsed as a decimal number. The returned {@link DecimalDecoder} supports
     * further numeric constraints such as {@code scale()}, {@code positive()}, etc.
     *
     * @return a decimal decoder with the parsed value
     */
    public DecimalDecoder<I> toDecimal() {
        return toDecimal(null);
    }

    /**
     * Parses the string as a {@link BigDecimal}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decimal decoder with the parsed value
     */
    public DecimalDecoder<I> toDecimal(String message) {
        return new DecimalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(new BigDecimal(value));
            } catch (NumberFormatException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TYPE_MISMATCH, message,
                                Map.of("expected", "decimal"))
                        : Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected decimal",
                                Map.of("expected", "decimal"));
            }
        }));
    }

    /**
     * Parses the string as a boolean.
     *
     * <p>Recognises common form-data representations (case-insensitive):
     * <ul>
     *   <li>true: {@code "true"}, {@code "1"}, {@code "yes"}, {@code "on"}</li>
     *   <li>false: {@code "false"}, {@code "0"}, {@code "no"}, {@code "off"}</li>
     * </ul>
     *
     * <p>Produces a {@link ErrorCodes#TYPE_MISMATCH} error for any other value.
     * The returned {@link BoolDecoder} supports {@code isTrue()} and {@code isFalse()} constraints.
     *
     * @return a boolean decoder with the parsed value
     */
    public BoolDecoder<I> toBool() {
        return toBool(null);
    }

    /**
     * Parses the string as a boolean.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a boolean decoder with the parsed value
     * @see #toBool()
     */
    public BoolDecoder<I> toBool(String message) {
        return new BoolDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            Boolean parsed = switch (value.toLowerCase()) {
                case "true", "1", "yes", "on" -> Boolean.TRUE;
                case "false", "0", "no", "off" -> Boolean.FALSE;
                default -> null;
            };
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return message != null
                    ? Result.failCustom(path, ErrorCodes.TYPE_MISMATCH, message,
                            Map.of("expected", "boolean"))
                    : Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected boolean",
                            Map.of("expected", "boolean"));
        }));
    }

    private StringDecoder<I> chain(Decoder<String, String> constraint) {
        return new StringDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
