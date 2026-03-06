package net.unit8.raoh.builtin;

import net.unit8.raoh.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
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

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://[^\\s/$.?#].[^\\s]*$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^([0-9a-fA-F]{1,4}:){1,7}:$|^:(:([0-9a-fA-F]{1,4})){1,7}$");
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

    public static <I> StringDecoder<I> from(Decoder<I, String> dec) {
        return new StringDecoder<>(dec);
    }

    // --- Constraints ---

    public StringDecoder<I> nonBlank() {
        return chain((value, path) -> {
            if (value == null || value.isBlank()) {
                return Result.fail(path, "required", "is required");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> allowBlank() {
        return base != null ? new StringDecoder<>(base) : this;
    }

    public StringDecoder<I> minLength(int n) {
        return minLength(n, null);
    }

    public StringDecoder<I> minLength(int n, String message) {
        return chain((value, path) -> {
            if (value.length() < n) {
                var meta = Map.<String, Object>of("min", n, "actual", value.length());
                return message != null
                        ? Result.failCustom(path, "too_short", message, meta)
                        : Result.fail(path, "too_short", "must be at least %d characters".formatted(n), meta);
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
                        ? Result.failCustom(path, "too_long", message, meta)
                        : Result.fail(path, "too_long", "must be at most %d characters".formatted(n), meta);
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
                        ? Result.failCustom(path, "invalid_length", message, meta)
                        : Result.fail(path, "invalid_length", "must be exactly %d characters".formatted(n), meta);
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> pattern(Pattern p) {
        return pattern(p, "invalid_format", null);
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
                        ? Result.failCustom(path, "invalid_format", message, meta)
                        : Result.fail(path, "invalid_format", "must start with \"%s\"".formatted(prefix), meta);
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
                        ? Result.failCustom(path, "invalid_format", message, meta)
                        : Result.fail(path, "invalid_format", "must end with \"%s\"".formatted(suffix), meta);
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
                        ? Result.failCustom(path, "invalid_format", message, meta)
                        : Result.fail(path, "invalid_format", "must include \"%s\"".formatted(substring), meta);
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
            if (!EMAIL_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid email");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> url() {
        return url(null);
    }

    public StringDecoder<I> url(String message) {
        return chain((value, path) -> {
            if (!URL_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid URL");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ipv4() {
        return ipv4(null);
    }

    public StringDecoder<I> ipv4(String message) {
        return chain((value, path) -> {
            if (!IPV4_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid IPv4 address");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ipv6() {
        return ipv6(null);
    }

    public StringDecoder<I> ipv6(String message) {
        return chain((value, path) -> {
            if (!IPV6_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid IPv6 address");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> ip() {
        return ip(null);
    }

    public StringDecoder<I> ip(String message) {
        return chain((value, path) -> {
            if (!IPV4_PATTERN.matcher(value).matches() && !IPV6_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid IP address");
            }
            return Result.ok(value);
        });
    }

    public StringDecoder<I> cuid() {
        return cuid(null);
    }

    public StringDecoder<I> cuid(String message) {
        return chain((value, path) -> {
            if (!CUID_PATTERN.matcher(value).matches()) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid CUID");
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
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid ULID");
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
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid UUID");
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
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid URI");
            }
        });
    }

    public Decoder<I, Instant> iso8601() {
        return iso8601(null);
    }

    public Decoder<I, Instant> iso8601(String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(Instant.parse(value));
            } catch (Exception e) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid ISO 8601 instant");
            }
        });
    }

    public Decoder<I, LocalDate> date() {
        return date(null);
    }

    public Decoder<I, LocalDate> date(String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(LocalDate.parse(value));
            } catch (Exception e) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid date (yyyy-MM-dd)");
            }
        });
    }

    public Decoder<I, LocalTime> time() {
        return time(null);
    }

    public Decoder<I, LocalTime> time(String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            try {
                return Result.ok(LocalTime.parse(value));
            } catch (Exception e) {
                return message != null
                        ? Result.failCustom(path, "invalid_format", message, Map.of())
                        : Result.fail(path, "invalid_format", "not a valid time (HH:mm:ss)");
            }
        });
    }

    private StringDecoder<I> chain(Decoder<String, String> constraint) {
        return new StringDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
