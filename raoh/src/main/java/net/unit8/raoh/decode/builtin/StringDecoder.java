package net.unit8.raoh.decode.builtin;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

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
import java.util.Locale;
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

    /**
     * Creates a new {@link StringDecoder} wrapping the given decoder.
     *
     * @param inner the underlying decoder that produces a string value
     */
    public StringDecoder(Decoder<I, String> inner) {
        this.inner = inner;
        this.base = null;
    }

    /**
     * Creates a new {@link StringDecoder} wrapping the given decoder, retaining a base decoder
     * for use by {@link #allowBlank()}.
     *
     * @param inner the underlying decoder that produces a string value
     * @param base  the original decoder before {@link #nonBlank()} was applied, or {@code null}
     */
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
     * @return a new decoder that accepts blank strings
     * @throws IllegalStateException if this decoder was not created via {@code net.unit8.raoh.json.JsonDecoders#string()}
     *         or {@link net.unit8.raoh.decode.ObjectDecoders#string()} (i.e., no base decoder is available to restore)
     */
    public StringDecoder<I> allowBlank() {
        if (base == null) {
            throw new IllegalStateException(
                    "allowBlank() can only be called on a StringDecoder created by JsonDecoders.string() " +
                    "or ObjectDecoders.string(), which retain the base decoder for restoration.");
        }
        return new StringDecoder<>(base);
    }

    /**
     * Restricts the string to be at least {@code n} characters long.
     *
     * @param n the minimum length
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SHORT} if shorter
     */
    public StringDecoder<I> minLength(int n) {
        return minLength(n, null);
    }

    /**
     * Restricts the string to be at least {@code n} characters long.
     *
     * @param n       the minimum length
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SHORT} if shorter
     */
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

    /**
     * Restricts the string to be at most {@code n} characters long.
     *
     * @param n the maximum length
     * @return a new decoder that fails with {@link ErrorCodes#TOO_LONG} if longer
     */
    public StringDecoder<I> maxLength(int n) {
        return maxLength(n, null);
    }

    /**
     * Restricts the string to be at most {@code n} characters long.
     *
     * @param n       the maximum length
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_LONG} if longer
     */
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

    /**
     * Restricts the string to be exactly {@code n} characters long.
     *
     * @param n the required length
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_LENGTH} if the length differs
     */
    public StringDecoder<I> fixedLength(int n) {
        return fixedLength(n, null);
    }

    /**
     * Restricts the string to be exactly {@code n} characters long.
     *
     * @param n       the required length
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_LENGTH} if the length differs
     */
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

    /**
     * Validates the string against the given regular expression pattern.
     *
     * @param p the pattern to match against
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value does not match
     */
    public StringDecoder<I> pattern(Pattern p) {
        return pattern(p, ErrorCodes.INVALID_FORMAT, null);
    }

    /**
     * Validates the string against the given regular expression pattern with a custom error code.
     *
     * @param p    the pattern to match against
     * @param code the error code to use on failure
     * @return a new decoder that fails with the specified error code if the value does not match
     */
    public StringDecoder<I> pattern(Pattern p, String code) {
        return pattern(p, code, null);
    }

    /**
     * Validates the string against the given regular expression pattern with a custom error code and message.
     *
     * @param p       the pattern to match against
     * @param code    the error code to use on failure
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with the specified error code if the value does not match
     */
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

    /**
     * Requires the string to start with the given prefix.
     *
     * @param prefix the required prefix
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the prefix is absent
     */
    public StringDecoder<I> startsWith(String prefix) {
        return startsWith(prefix, null);
    }

    /**
     * Requires the string to start with the given prefix.
     *
     * @param prefix  the required prefix
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the prefix is absent
     */
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

    /**
     * Requires the string to end with the given suffix.
     *
     * @param suffix the required suffix
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the suffix is absent
     */
    public StringDecoder<I> endsWith(String suffix) {
        return endsWith(suffix, null);
    }

    /**
     * Requires the string to end with the given suffix.
     *
     * @param suffix  the required suffix
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the suffix is absent
     */
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

    /**
     * Requires the string to contain the given substring.
     *
     * @param substring the required substring
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the substring is absent
     */
    public StringDecoder<I> includes(String substring) {
        return includes(substring, null);
    }

    /**
     * Requires the string to contain the given substring.
     *
     * @param substring the required substring
     * @param message   custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the substring is absent
     */
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

    /**
     * Validates that the string is a well-formed email address.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid email
     */
    public StringDecoder<I> email() {
        return email(null);
    }

    /**
     * Validates that the string is a well-formed email address.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid email
     */
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

    /**
     * Decodes the string value to a {@link URI}, validating that it is a valid http or https URL.
     *
     * <p>Requires an absolute URL with the {@code http} or {@code https} scheme and a
     * non-empty host. The maximum accepted length is 2048 characters.
     * Uses {@link URI} parsing to avoid ReDoS from regex backtracking.
     *
     * <p>This is a terminal method — the returned decoder produces {@link URI},
     * not {@link String}, so no further {@link StringDecoder} constraints can be chained.
     * For URLs that accept any scheme, use {@link #uri()}.
     *
     * @return a decoder producing {@link URI} from validated http/https URLs
     */
    public Decoder<I, URI> url() {
        return url(null);
    }

    /**
     * Decodes the string value to a {@link URI}, validating that it is a valid http or https URL.
     *
     * <p>Requires an absolute URL with the {@code http} or {@code https} scheme and a
     * non-empty host. The maximum accepted length is 2048 characters.
     * Uses {@link URI} parsing to avoid ReDoS from regex backtracking.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decoder producing {@link URI} from validated http/https URLs
     */
    public Decoder<I, URI> url(String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
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
                return Result.ok(uri);
            } catch (IllegalArgumentException e) {
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_FORMAT, message, Map.of())
                        : Result.fail(path, ErrorCodes.INVALID_FORMAT, "not a valid URL");
            }
        });
    }

    /**
     * Validates that the string is a valid IPv4 address.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IPv4 address
     */
    public StringDecoder<I> ipv4() {
        return ipv4(null);
    }

    /**
     * Validates that the string is a valid IPv4 address.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IPv4 address
     */
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

    /**
     * Validates that the string is a valid IPv6 address.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IPv6 address
     */
    public StringDecoder<I> ipv6() {
        return ipv6(null);
    }

    /**
     * Validates that the string is a valid IPv6 address.
     * Uses {@link InetAddress#getByName} to avoid ReDoS from complex regex backtracking.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IPv6 address
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

    /**
     * Validates that the string is a valid IP address (IPv4 or IPv6).
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IP address
     */
    public StringDecoder<I> ip() {
        return ip(null);
    }

    /**
     * Validates that the string is a valid IP address (IPv4 or IPv6).
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IP address
     */
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

    /**
     * Validates that the string is a valid CUID.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid CUID
     */
    public StringDecoder<I> cuid() {
        return cuid(null);
    }

    /**
     * Validates that the string is a valid CUID.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid CUID
     */
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

    /**
     * Validates that the string is a valid ULID.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid ULID
     */
    public StringDecoder<I> ulid() {
        return ulid(null);
    }

    /**
     * Validates that the string is a valid ULID.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid ULID
     */
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

    /**
     * Trims leading and trailing whitespace from the decoded string.
     *
     * @return a new decoder that applies {@link String#trim()} to the value
     */
    public StringDecoder<I> trim() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(String::trim));
    }

    /**
     * Converts the decoded string to lower case.
     *
     * @return a new decoder that applies {@link String#toLowerCase()} to the value
     */
    public StringDecoder<I> toLowerCase() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(String::toLowerCase));
    }

    /**
     * Converts the decoded string to upper case.
     *
     * @return a new decoder that applies {@link String#toUpperCase()} to the value
     */
    public StringDecoder<I> toUpperCase() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(String::toUpperCase));
    }

    // --- Type conversions ---

    /**
     * Parses the string as a {@link UUID}.
     *
     * @return a decoder producing {@link UUID} from validated UUID strings
     */
    public Decoder<I, UUID> uuid() {
        return uuid(null);
    }

    /**
     * Parses the string as a {@link UUID}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decoder producing {@link UUID} from validated UUID strings
     */
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

    /**
     * Parses the string as a {@link URI}.
     *
     * <p>Unlike {@link #url()}, this accepts any valid URI regardless of scheme.
     *
     * @return a decoder producing {@link URI} from validated URI strings
     */
    public Decoder<I, URI> uri() {
        return uri(null);
    }

    /**
     * Parses the string as a {@link URI}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decoder producing {@link URI} from validated URI strings
     */
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
     * <p>Recognises common form-data representations (case-insensitive):</p>
     * <ul>
     *   <li>true: {@code "true"}, {@code "1"}, {@code "yes"}, {@code "on"}</li>
     *   <li>false: {@code "false"}, {@code "0"}, {@code "no"}, {@code "off"}</li>
     * </ul>
     *
     * <p>Produces a {@link ErrorCodes#TYPE_MISMATCH} error for any other value.
     * The returned {@link BoolDecoder} supports {@code isTrue()} and {@code isFalse()} constraints.</p>
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
            Boolean parsed = switch (value.toLowerCase(Locale.ROOT)) {
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
