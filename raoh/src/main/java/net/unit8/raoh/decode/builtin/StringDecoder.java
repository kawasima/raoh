package net.unit8.raoh.decode.builtin;

import net.unit8.notation199x.CaseConversion;
import net.unit8.notation199x.Normalization;
import net.unit8.notation199x.ScalarValues;
import net.unit8.notation199x.pattern.PatternMachine;
import net.unit8.notation199x.pattern.PatternParser;
import net.unit8.notation199x.pattern.PatternRead;
import net.unit8.notation199x.pattern.StringPattern;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.internal.DecimalConversion;
import net.unit8.raoh.CodePointOrder;
import net.unit8.raoh.ErrorCodes;
import net.unit8.raoh.MessageKeys;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A decoder for string values with a fluent API for constraints, transforms, and type conversions.
 *
 * <p>Constraints (e.g., {@link #minLength}, {@link #email}) and transforms (e.g., {@link #trim}, {@link #toLowerCase})
 * are chained to produce new decoders. Type conversions (e.g., {@link #uuid}, {@link #iso8601}) return
 * decoders of the converted type.
 *
 * @param <I> the input type
 */
public final class StringDecoder<I extends @Nullable Object> implements Decoder<I, String> {

    private static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]{1,64}@[a-zA-Z0-9.\\-]{1,255}\\.[a-zA-Z]{2,}$");
    private static final Pattern CUID_PATTERN = Pattern.compile(
            "^c[a-z0-9]{24}$");
    // Crockford's base 32 in either case. 26 characters hold 130 bits, and a ULID is 128: the first
    // character carries the top 3 bits, so it is at most 7.
    private static final Pattern ULID_PATTERN = Pattern.compile(
            "^[0-7][0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{25}$");

    private final Decoder<I, String> inner;

    /**
     * Creates a new {@link StringDecoder} wrapping the given decoder.
     *
     * @param inner the underlying decoder that produces a string value
     */
    public StringDecoder(Decoder<I, String> inner) {
        this.inner = inner;
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
     * <p>Whitespace is the Unicode {@code White_Space} property, pinned to Unicode 18.0.0 (for
     * example U+0020, U+00A0, U+2003 and U+3000; not U+200B or U+0000). {@link #trim()} strips
     * the same characters, so a value is blank exactly when {@code trim()} leaves it empty.
     *
     * <p>Fails with {@link ErrorCodes#BLANK} when the decoded string is empty or consists
     * entirely of whitespace. Use this after {@link #trim()} to reject strings that become
     * empty after trimming, or stand-alone to reject blank-only input.
     *
     * @return a new decoder that fails with {@link ErrorCodes#BLANK} for blank values
     */
    public StringDecoder<I> nonBlank() {
        return chain((value, path) -> {
            if (Whitespace.isBlank(value)) {
                return Result.fail(path, ErrorCodes.BLANK, "must not be blank");
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the string to be at least {@code n} characters long, counted in Unicode code
     * points via {@link String#codePointCount(int, int)}, so a supplementary-plane character
     * counts as one.
     *
     * @param n the minimum length
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SHORT} if shorter
     */
    public StringDecoder<I> minLength(int n) {
        return minLength(n, null);
    }

    /**
     * Restricts the string to be at least {@code n} characters long, counted in Unicode code
     * points via {@link String#codePointCount(int, int)}, so a supplementary-plane character
     * counts as one.
     *
     * @param n       the minimum length
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_SHORT} if shorter
     */
    public StringDecoder<I> minLength(int n, @Nullable String message) {
        return chain((value, path) -> {
            int length = codePointLength(value);
            if (length < n) {
                var meta = Map.<String, Object>of("min", n, "actual", length);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TOO_SHORT, message, meta)
                        : Result.fail(path, ErrorCodes.TOO_SHORT, String.format(Locale.ROOT, "must be at least %d characters", n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the string to be at most {@code n} characters long, counted in Unicode code
     * points via {@link String#codePointCount(int, int)}, so a supplementary-plane character
     * counts as one.
     *
     * @param n the maximum length
     * @return a new decoder that fails with {@link ErrorCodes#TOO_LONG} if longer
     */
    public StringDecoder<I> maxLength(int n) {
        return maxLength(n, null);
    }

    /**
     * Restricts the string to be at most {@code n} characters long, counted in Unicode code
     * points via {@link String#codePointCount(int, int)}, so a supplementary-plane character
     * counts as one.
     *
     * @param n       the maximum length
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#TOO_LONG} if longer
     */
    public StringDecoder<I> maxLength(int n, @Nullable String message) {
        return chain((value, path) -> {
            int length = codePointLength(value);
            if (length > n) {
                var meta = Map.<String, Object>of("max", n, "actual", length);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.TOO_LONG, message, meta)
                        : Result.fail(path, ErrorCodes.TOO_LONG, String.format(Locale.ROOT, "must be at most %d characters", n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the string to be exactly {@code n} characters long, counted in Unicode code
     * points via {@link String#codePointCount(int, int)}, so a supplementary-plane character
     * counts as one.
     *
     * @param n the required length
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_LENGTH} if the length differs
     */
    public StringDecoder<I> fixedLength(int n) {
        return fixedLength(n, null);
    }

    /**
     * Restricts the string to be exactly {@code n} characters long, counted in Unicode code
     * points via {@link String#codePointCount(int, int)}, so a supplementary-plane character
     * counts as one.
     *
     * @param n       the required length
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_LENGTH} if the length differs
     */
    public StringDecoder<I> fixedLength(int n, @Nullable String message) {
        return chain((value, path) -> {
            int length = codePointLength(value);
            if (length != n) {
                var meta = Map.<String, Object>of("expected", n, "actual", length);
                return message != null
                        ? Result.failCustom(path, ErrorCodes.INVALID_LENGTH, message, meta)
                        : Result.fail(path, ErrorCodes.INVALID_LENGTH, String.format(Locale.ROOT, "must be exactly %d characters", n), meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Restricts the decoded value to one of the specified allowed values.
     *
     * <p>The issue lists the allowed values in {@link CodePointOrder code point order}.
     *
     * @param allowed the set of allowed string values
     * @return a new decoder that fails with {@link ErrorCodes#NOT_ALLOWED} if the value is not in the set
     */
    public StringDecoder<I> oneOf(String... allowed) {
        var allowedSet = Set.of(allowed);
        var sortedAllowed = CodePointOrder.sorted(allowedSet);
        var message = String.format(Locale.ROOT, "must be one of %s", sortedAllowed);
        return chain((value, path) -> {
            if (!allowedSet.contains(value)) {
                var meta = Map.<String, Object>of("allowed", sortedAllowed, "actual", value);
                return Result.fail(path, ErrorCodes.NOT_ALLOWED, message, meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Requires the whole string to be one of the strings the pattern accepts.
     *
     * <p>See {@link #pattern(String, String, String)} for the pattern language.
     *
     * @param pattern the pattern, in the pattern language of the Raoh Specification
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value does not match
     * @throws IllegalArgumentException if {@code pattern} is not a pattern of the language, or is
     *                                  past one of the limits on an admissible pattern
     */
    public StringDecoder<I> pattern(String pattern) {
        return pattern(pattern, ErrorCodes.INVALID_FORMAT, null);
    }

    /**
     * Requires the whole string to be one of the strings the pattern accepts, with a custom error
     * code.
     *
     * <p>See {@link #pattern(String, String, String)} for the pattern language.
     *
     * @param pattern the pattern, in the pattern language of the Raoh Specification
     * @param code    the error code to use on failure
     * @return a new decoder that fails with the specified error code if the value does not match
     * @throws IllegalArgumentException if {@code pattern} is not a pattern of the language, or is
     *                                  past one of the limits on an admissible pattern
     */
    public StringDecoder<I> pattern(String pattern, String code) {
        return pattern(pattern, code, null);
    }

    /**
     * Requires the whole string to be one of the strings the pattern accepts, with a custom error
     * code and message.
     *
     * <p>The pattern is text in the pattern language of the Raoh Specification, the one Souther
     * uses: literals, classes, {@code .}, the shorthands {@code \d}, {@code \w} and {@code \s}
     * over ASCII, groups, alternation, counted repetition, and {@code ^} and {@code $} at the ends.
     * It always matches the whole value, so the anchors may be left out. Text that is a regular
     * expression elsewhere and not a pattern of this language is refused when the decoder is
     * built: a back reference, a lookaround, a named or flag group, a property class such as
     * {@code \p{L}}, a boundary such as {@code \b}, a possessive repetition. There are no flags.
     *
     * <p>A pattern is admitted within three limits, the same in every implementation of the
     * specification and each decided from the text: a count of at most 134217727, groups nested
     * at most 200 deep, and at most 250000 states once its repetitions are written out, counted
     * on the pattern as written (a set of characters or an anchor is one, a choice of {@code n}
     * alternatives is one plus one more than each, {@code A{n,m}} is {@code m} times {@code A}
     * plus one, {@code A{n,}} is {@code n + 1} times {@code A} plus one, and the pattern is one
     * more). So {@code a{249998}} is admitted and {@code a{249999}} is not. A pattern past a limit
     * is refused when the decoder is built, with a message that names the limit.
     *
     * <p>The value is matched by the set of strings the pattern means and not by
     * {@code java.util.regex}. A match reads each character of the value once, so the time it
     * takes is linear in the length of the value whatever the pattern, and the result does not
     * depend on the Unicode data of the running JDK. A value holding an unpaired surrogate matches
     * no pattern.
     *
     * @param pattern the pattern, in the pattern language of the Raoh Specification
     * @param code    the error code to use on failure
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with the specified error code if the value does not match
     * @throws IllegalArgumentException if {@code pattern} is not a pattern of the language, or is
     *                                  past one of the limits on an admissible pattern
     */
    public StringDecoder<I> pattern(String pattern, String code, @Nullable String message) {
        Objects.requireNonNull(pattern, "pattern");
        var compiled = compile(pattern);
        return chain((value, path) -> {
            if (!compiled.matches(value)) {
                var meta = Map.<String, Object>of("pattern", pattern);
                return message != null
                        ? Result.failCustom(path, code, message, meta)
                        : Result.fail(path, code, "invalid format", meta);
            }
            return Result.ok(value);
        });
    }

    /**
     * Reads a pattern and builds what values are matched against.
     *
     * <p>Every pattern the reader admits has a machine, so the only refusals are the reader's: text
     * that is no pattern, and a pattern past one of the limits. They are told apart in the message,
     * since what an author does about them differs.
     *
     * @param pattern the pattern text
     * @return the machine the pattern means
     * @throws IllegalArgumentException if the text is not a pattern, or is past a limit
     */
    private static StringPattern compile(String pattern) {
        var read = PatternParser.read(pattern);
        if (read instanceof PatternRead.Refused refused) {
            throw new IllegalArgumentException("not a pattern of the Raoh pattern language: \""
                    + pattern + "\" (" + refused.why().name() + " at index " + refused.from()
                    + (refused.construct().isEmpty() ? "" : ": \"" + refused.construct() + "\"") + ")");
        }
        if (read instanceof PatternRead.Beyond beyond) {
            throw new IllegalArgumentException("pattern \"" + pattern + "\" is past the limit of "
                    + beyond.limit().most() + " " + limitName(beyond.limit())
                    + (beyond.from() == 0 && beyond.construct().equals(pattern)
                            ? "" : " at index " + beyond.from() + ": \"" + beyond.construct() + "\""));
        }
        return PatternMachine.of(((PatternRead.Read) read).meaning()).pattern();
    }

    /**
     * What a limit on an admissible pattern counts, for a message.
     *
     * <p>Not a switch: javac would compile one through the enum's ordinals.
     *
     * @param limit the limit
     * @return the thing it counts
     * @throws IllegalStateException if the limit is one this Raoh does not know
     */
    private static String limitName(PatternRead.Limit limit) {
        if (limit == PatternRead.Limit.REPETITION_COUNT) {
            return "on a repetition count";
        }
        if (limit == PatternRead.Limit.NESTING_DEPTH) {
            return "on groups nested one inside another";
        }
        if (limit == PatternRead.Limit.MACHINE_STATES) {
            return "states once its repetitions are written out";
        }
        // A limit added to 199x-notation after this was written: a version this Raoh was not built
        // against, and not something wrong with the caller's pattern.
        throw new IllegalStateException("199x-notation reports a pattern limit this Raoh does not know: "
                + limit.name());
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
    public StringDecoder<I> startsWith(String prefix, @Nullable String message) {
        return chain((value, path) -> {
            if (!value.startsWith(prefix)) {
                var meta = Map.<String, Object>of("prefix", prefix);
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_STARTS_WITH,
                        message, String.format(Locale.ROOT, "must start with \"%s\"", prefix), meta);
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
    public StringDecoder<I> endsWith(String suffix, @Nullable String message) {
        return chain((value, path) -> {
            if (!value.endsWith(suffix)) {
                var meta = Map.<String, Object>of("suffix", suffix);
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_ENDS_WITH,
                        message, String.format(Locale.ROOT, "must end with \"%s\"", suffix), meta);
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
    public StringDecoder<I> includes(String substring, @Nullable String message) {
        return chain((value, path) -> {
            if (!value.contains(substring)) {
                var meta = Map.<String, Object>of("substring", substring);
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_INCLUDES,
                        message, String.format(Locale.ROOT, "must include \"%s\"", substring), meta);
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
    public StringDecoder<I> email(@Nullable String message) {
        return chain((value, path) -> {
            if (value.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_EMAIL,
                        message, "not a valid email", Map.of());
            }
            return Result.ok(value);
        });
    }

    /**
     * Decodes the string value to a {@link URI}, validating that it is an http or https URL.
     * See {@link #url(String)} for the accepted text.
     *
     * <p>This is a terminal method — the returned decoder produces {@link URI},
     * not {@link String}, so no further {@link StringDecoder} constraints can be chained.
     * For URIs of any scheme, use {@link #uri()}.
     *
     * @return a decoder producing {@link URI} from validated http/https URLs
     */
    public Decoder<I, URI> url() {
        return url(null);
    }

    /**
     * Decodes the string value to a {@link URI}, validating that it is an http or https URL.
     *
     * <p>The text is a URI that {@link #uri(String)} accepts, with the {@code http} or
     * {@code https} scheme in any case, an authority, and a non-empty host (RFC 9110 section
     * 4.2). The host is the RFC 3986 {@code host}, not a DNS name: a {@code reg-name} such as
     * {@code my_host} or {@code example.123} is accepted. The length is not limited; put
     * {@link #maxLength(int)} before this conversion to bound it.
     *
     * <p>The returned {@link URI} holds the accepted text, but its component accessors follow the
     * RFC 2396 model of {@code java.net.URI}, so {@link URI#getHost()} can be {@code null} for a
     * {@code reg-name} this decoder accepts. Consumers with narrower URI requirements, including
     * some JDK networking APIs, may reject such a value.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decoder producing {@link URI} from validated http/https URLs
     */
    public Decoder<I, URI> url(@Nullable String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = UriSyntax.parse(value);
            if (parsed == null || !parsed.representableAsJavaUri() || !isHttpUrl(parsed)) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_URL,
                        message, "not a valid URL", Map.of());
            }
            return Result.ok(URI.create(value));
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
    public StringDecoder<I> ipv4(@Nullable String message) {
        return chain((value, path) -> {
            if (!isIPv4(value)) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_IPV4,
                        message, "not a valid IPv4 address", Map.of());
            }
            return Result.ok(value);
        });
    }

    /**
     * Validates that the string is a valid IPv6 address.
     * See {@link #ipv6(String)} for the accepted syntax.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IPv6 address
     */
    public StringDecoder<I> ipv6() {
        return ipv6(null);
    }

    /**
     * Validates that the string is a valid IPv6 address.
     *
     * <p>The address is the RFC 4291 section 2.2 text form, as RFC 3986 section 3.2.2 writes it
     * in the {@code IPv6address} rule: eight groups of one to four hexadecimal digits, at most one
     * {@code ::} standing for one or more zero groups, and optionally an IPv4 address in place of the
     * last two groups, such as {@code ::ffff:192.0.2.1}. So {@code ::00001} is rejected, and so is
     * {@code ::01.2.3.4}, because the embedded IPv4 address follows the rules of
     * {@link #ipv4(String)}. Brackets ({@code [::1]}) belong to the URI host syntax and are rejected.
     *
     * <p>The address may be followed by an RFC 4007 zone ID ({@code fe80::1%eth0}) when its scope is
     * below global: link-local unicast ({@code fe80::/10}) or multicast whose scope is 1 to D
     * (RFC 4291 section 2.7, as updated by RFC 7346). The zone ID is taken as an opaque, non-empty
     * string that contains neither {@code %} nor NUL. The decoder does not look the zone up among
     * the host's network interfaces, so the result does not depend on the machine it runs on.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IPv6 address
     */
    public StringDecoder<I> ipv6(@Nullable String message) {
        return chain((value, path) -> {
            if (!isIPv6(value)) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_IPV6,
                        message, "not a valid IPv6 address", Map.of());
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
     * An IPv4 address is a dotted quad; an IPv6 address follows the rules of {@link #ipv6(String)}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid IP address
     */
    public StringDecoder<I> ip(@Nullable String message) {
        return chain((value, path) -> {
            if (!isIPv4(value) && !isIPv6(value)) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_IP,
                        message, "not a valid IP address", Map.of());
            }
            return Result.ok(value);
        });
    }

    private static boolean isIPv4(String value) {
        return IpSyntax.isIpv4(value);
    }

    private static boolean isIPv6(String value) {
        int zoneAt = value.indexOf('%');
        if (zoneAt < 0) {
            return IpSyntax.isIpv6(value);
        }
        if (zoneAt == value.length() - 1
                || value.indexOf('%', zoneAt + 1) >= 0
                || value.indexOf('\0', zoneAt + 1) >= 0) {
            return false;
        }
        var address = value.substring(0, zoneAt);
        return IpSyntax.isIpv6(address) && canHaveZone(IpSyntax.ipv6FirstGroup(address));
    }

    // Whether a zone ID may follow the address, decided from its first 16-bit group as RFC 4291 and
    // its updates define the scopes, rather than by Inet6Address's classifiers: isSiteLocalAddress()
    // still reports the deprecated fec0::/10 range, which RFC 4291 says to treat as global unicast,
    // and isMCGlobal() is false for the reserved multicast scopes 0 and F.
    private static boolean canHaveZone(int firstGroup) {
        int first = firstGroup >> 8;
        int second = firstGroup & 0xff;
        // Link-local unicast, fe80::/10 (RFC 4291 section 2.5.6).
        if (first == 0xfe && (second & 0xc0) == 0x80) {
            return true;
        }
        if (first != 0xff) {
            return false;
        }
        // Multicast scope, the low 4 bits of the second byte (RFC 4291 section 2.7, as updated by
        // RFC 7346), read apart from the flag bits (RFC 7371). Everything below global scope takes
        // a zone: interface-local (1), link-local (2), realm-local (3, RFC 7346), admin-local (4),
        // site-local (5), organization-local (8), and the unassigned 6, 7 and 9 to D, which
        // administrators may define as further regions. 0 is reserved, E is global, and F is
        // reserved and handled like global.
        return switch (second & 0x0f) {
            case 0x0, 0xe, 0xf -> false;
            default -> true;
        };
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
    public StringDecoder<I> cuid(@Nullable String message) {
        return chain((value, path) -> {
            if (!CUID_PATTERN.matcher(value).matches()) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_CUID,
                        message, "not a valid CUID", Map.of());
            }
            return Result.ok(value);
        });
    }

    /**
     * Validates that the string is a valid ULID.
     *
     * <p>A ULID is 26 characters of Crockford's base 32 ({@code 0}-{@code 9} and the letters but
     * {@code I}, {@code L}, {@code O} and {@code U}), in either case, whose value fits in 128 bits:
     * the first character is {@code 0} to {@code 7}, so {@code 7ZZZZZZZZZZZZZZZZZZZZZZZZZ} is the
     * largest. The string is given unchanged, not converted to upper case.
     *
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid ULID
     */
    public StringDecoder<I> ulid() {
        return ulid(null);
    }

    /**
     * Validates that the string is a valid ULID.
     *
     * <p>A ULID is 26 characters of Crockford's base 32 ({@code 0}-{@code 9} and the letters but
     * {@code I}, {@code L}, {@code O} and {@code U}), in either case, whose value fits in 128 bits:
     * the first character is {@code 0} to {@code 7}, so {@code 7ZZZZZZZZZZZZZZZZZZZZZZZZZ} is the
     * largest. The string is given unchanged, not converted to upper case.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a new decoder that fails with {@link ErrorCodes#INVALID_FORMAT} if the value is not a valid ULID
     */
    public StringDecoder<I> ulid(@Nullable String message) {
        return chain((value, path) -> {
            if (!ULID_PATTERN.matcher(value).matches()) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_ULID,
                        message, "not a valid ULID", Map.of());
            }
            return Result.ok(value);
        });
    }

    // --- Transforms ---

    /**
     * Trims leading and trailing whitespace from the decoded string.
     *
     * <p>Whitespace is the same set {@link #nonBlank()} uses: the Unicode {@code White_Space}
     * property, pinned to Unicode 18.0.0. This differs from {@link String#trim()}, which strips
     * every character up to U+0020 and keeps U+00A0 and U+3000.
     *
     * @return a new decoder that removes leading and trailing whitespace from the value
     */
    public StringDecoder<I> trim() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(Whitespace::trim));
    }

    /**
     * Converts the decoded string to lower case with Unicode 18.0.0's default case conversion.
     *
     * <p>The mapping is the full one of {@code UnicodeData.txt} and {@code SpecialCasing.txt}
     * with no locale or language tailoring, so {@code "TITLE"} becomes {@code "title"} whatever
     * the JVM default locale is. A Greek capital sigma becomes the final form {@code ς} by
     * Unicode's {@code Final_Sigma} condition: {@code "ΟΣ"} becomes {@code "ος"}, and
     * {@code "Α1Σ"} becomes {@code "α1σ"} because a digit is not case-ignorable. The result does
     * not depend on the Unicode version of the running JDK, which
     * {@link String#toLowerCase(Locale)} follows. For a locale-specific case mapping, use
     * {@code map(s -> s.toLowerCase(locale))} instead.
     *
     * @return a new decoder that converts the value to lower case
     */
    public StringDecoder<I> toLowerCase() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(CaseConversion::lowercase));
    }

    /**
     * Converts the decoded string to upper case with Unicode 18.0.0's default case conversion.
     *
     * <p>The mapping is the full one of {@code UnicodeData.txt} and {@code SpecialCasing.txt}
     * with no locale or language tailoring, so {@code "title"} becomes {@code "TITLE"} whatever
     * the JVM default locale is, and one character can become several ({@code "straße"} becomes
     * {@code "STRASSE"}). The result does not depend on the Unicode version of the running JDK,
     * which {@link String#toUpperCase(Locale)} follows. For a locale-specific case mapping, use
     * {@code map(s -> s.toUpperCase(locale))} instead.
     *
     * @return a new decoder that converts the value to upper case
     */
    public StringDecoder<I> toUpperCase() {
        return new StringDecoder<>((in, path) -> this.decode(in, path).map(CaseConversion::uppercase));
    }

    /**
     * Normalizes the decoded string to {@link Normalizer.Form#NFC}.
     *
     * <p>Canonically equivalent inputs — {@code U+304C} and {@code U+304B U+3099}, the same が
     * composed and decomposed — become the same string, so the constraints that follow no longer
     * depend on how the client happened to encode the text. Decomposed text is not exotic: filenames
     * originating from HFS+, and some macOS, IME and clipboard paths, deliver it in that form.
     *
     * @return a new decoder that applies {@link Normalizer.Form#NFC} to the value
     */
    public StringDecoder<I> normalize() {
        return normalize(Normalizer.Form.NFC);
    }

    /**
     * Normalizes the decoded string to the given Unicode normalization form.
     *
     * <p>How much the form unifies is the choice being made here. NFC and NFD unify canonically
     * equivalent strings and keep compatibility distinctions; NFKC and NFKD fold compatibility
     * equivalents as well, erasing the difference between halfwidth ｱ and fullwidth ア and between
     * ㍿ and 株式会社. That suits a search key and discards information a stored name is meant to
     * keep, which is why {@link #normalize()} is NFC.
     *
     * <p>Every form is Unicode 18.0.0 normalization, whatever Unicode version the running JDK's
     * {@link Normalizer} has. {@link Normalizer.Form} only names the form here.
     *
     * <p>Normalization is a transform, not a constraint, and it composes in the order it is
     * written: {@code string().normalize().maxLength(20)} counts the normalized value, whereas
     * {@code string().maxLength(20).normalize()} checks the length of the input as it arrived.
     *
     * <p>What it does not do: variation sequences are normalization-stable by design, so 葛 followed
     * by {@code U+E0101} stays two code points under every form. Nor does normalization produce a
     * count of user-perceived characters — that is grapheme cluster segmentation, a different
     * constraint rather than a more accurate version of this one.
     *
     * <p>The value is normalized; the arguments of later constraints are not. A literal passed to
     * {@link #oneOf} or {@link #startsWith} therefore has to be written in the same form, or
     * normalized explicitly, to match. The Java language does not normalize string literals — a
     * literal holds whatever the source file or the escape sequence spells out — so a decomposed
     * literal stays decomposed and will not match a value normalized to NFC.
     *
     * @param form the normalization form to apply
     * @return a new decoder that applies {@code form} to the value
     */
    public StringDecoder<I> normalize(Normalizer.Form form) {
        Objects.requireNonNull(form, "form");
        var unicodeForm = unicodeForm(form);
        return new StringDecoder<>((in, path) ->
                this.decode(in, path).map(value -> Normalization.normalize(unicodeForm, value)));
    }

    /**
     * The Unicode 18.0.0 form a {@link Normalizer.Form} names.
     *
     * <p>Not a switch: javac would compile one through {@code Normalizer.Form}'s ordinals.
     *
     * @param form the form as the JDK names it
     * @return the same form in 199x-notation
     * @throws IllegalArgumentException if a later JDK names a form Unicode 18.0.0 does not have
     */
    private static Normalization.Form unicodeForm(Normalizer.Form form) {
        if (form == Normalizer.Form.NFC) {
            return Normalization.Form.NFC;
        }
        if (form == Normalizer.Form.NFD) {
            return Normalization.Form.NFD;
        }
        if (form == Normalizer.Form.NFKC) {
            return Normalization.Form.NFKC;
        }
        if (form == Normalizer.Form.NFKD) {
            return Normalization.Form.NFKD;
        }
        throw new IllegalArgumentException("not a Unicode 18.0.0 normalization form: " + form.name());
    }

    // --- Type conversions ---

    /**
     * Parses the string as a {@link UUID}.
     *
     * <p>See {@link #uuid(String)} for the accepted text.
     *
     * @return a decoder producing {@link UUID} from validated UUID strings
     */
    public Decoder<I, UUID> uuid() {
        return uuid(null);
    }

    /**
     * Parses the string as a {@link UUID}.
     *
     * <p>The accepted text is the RFC 9562 form: 32 hexadecimal digits grouped
     * {@code 8-4-4-4-12} by hyphens, in upper, lower or mixed case (e.g.
     * {@code 550e8400-e29b-41d4-a716-446655440000}). Shortened groups such as {@code 1-1-1-1-1},
     * braces and a {@code urn:uuid:} prefix are rejected with {@code invalid_format}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decoder producing {@link UUID} from validated UUID strings
     */
    public Decoder<I, UUID> uuid(@Nullable String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            if (!LexicalRules.isUuid(value)) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_UUID,
                        message, "not a valid UUID", Map.of());
            }
            return Result.ok(UUID.fromString(value));
        });
    }

    // RFC 9110 section 4.2: the http or https scheme, compared without case, and a non-empty host.
    private static boolean isHttpUrl(UriSyntax.Parsed parsed) {
        return (parsed.schemeIs("http") || parsed.schemeIs("https")) && parsed.hasHost();
    }

    /**
     * Parses the string as a {@link URI}.
     * See {@link #uri(String)} for the accepted text.
     *
     * <p>Unlike {@link #url()}, this accepts any scheme.
     *
     * @return a decoder producing {@link URI} from validated URI strings
     */
    public Decoder<I, URI> uri() {
        return uri(null);
    }

    /**
     * Parses the string as a {@link URI}.
     *
     * <p>The text is a {@code URI} by RFC 3986 section 3 that {@link URI} can hold: a scheme, a
     * colon and the rest, with every character outside the grammar percent-encoded. A relative reference such as
     * {@code foo/bar} or {@code #top} has no scheme and is rejected, as are raw non-ASCII
     * characters. An IPv6 host follows the rules of {@link #ipv6(String)} without a zone ID, which
     * RFC 9844 removed from URIs, so {@code http://[fe80::1%25eth0]/} is rejected.
     *
     * <p>{@link URI} follows the older RFC 2396 and RFC 2732 and cannot hold every RFC 3986 URI.
     * Those this decoder cannot return are rejected: an empty scheme-specific part
     * ({@code a:}, {@code a:#f}), an empty authority followed by nothing ({@code a://}), an
     * {@code IPvFuture} host ({@code http://[v1.abc]/}), and an IPv6 host with a port above
     * {@link Integer#MAX_VALUE} ({@code http://[::1]:2147483648/}). The returned {@link URI} holds the accepted
     * text, but its component accessors follow the RFC 2396 model, so {@link URI#getHost()} can be
     * {@code null} for an RFC 3986 {@code reg-name} such as {@code my_host}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decoder producing {@link URI} from validated URI strings
     */
    public Decoder<I, URI> uri(@Nullable String message) {
        return (in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = UriSyntax.parse(value);
            if (parsed == null || !parsed.representableAsJavaUri()) {
                return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_URI,
                        message, "not a valid URI", Map.of());
            }
            return Result.ok(URI.create(value));
        });
    }

    /**
     * Parses the string as an ISO 8601 instant (e.g., {@code 2024-01-15T10:30:00Z}).
     *
     * <p>See {@link #iso8601(String)} for the accepted text.
     *
     * @return a temporal decoder producing {@link Instant}
     */
    public TemporalDecoder<I, Instant> iso8601() {
        return iso8601(null);
    }

    /**
     * Parses the string as an ISO 8601 instant.
     *
     * <p>The accepted text is {@code yyyy-MM-ddTHH:mm:ss}, an optional fraction of 1 to 9 digits,
     * and an offset {@code Z}, {@code ±HH:mm} or {@code ±HH:mm:ss} (e.g.
     * {@code 2024-01-15T10:30:00Z}, {@code 2024-01-15T10:30:00.5+09:00}). Seconds are required. The
     * {@code T} and {@code Z} are upper case only. The year is written as in {@link #date(String)},
     * as {@link Instant#toString()} writes it, and the whole {@link Instant} range is accepted.
     * The offset is applied, so the result is the moment the text names.
     *
     * <p>A clock time of {@code 23:59:60}, at any offset, is rejected with {@code invalid_format}:
     * a leap second is a moment {@link Instant} cannot name. An end of day {@code 24:00:00} is
     * accepted as the start of the next day, the same instant, only with no fraction:
     * {@code 24:00:01}, {@code 24:00:00.5} and {@code 24:00:00.0} are rejected.
     * {@link #offsetDateTime(String)} reads a different form, a clock's date and time beside an
     * offset, and rejects hour {@code 24}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link Instant}
     */
    public TemporalDecoder<I, Instant> iso8601(@Nullable String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = TemporalText.instant(value);
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_INSTANT,
                    message, "not a valid ISO 8601 instant", Map.of());
        }));
    }

    /**
     * Parses the string as a local date (e.g., {@code 2024-01-15}).
     *
     * <p>See {@link #date(String)} for the accepted text.
     *
     * @return a temporal decoder producing {@link LocalDate}
     */
    public TemporalDecoder<I, LocalDate> date() {
        return date(null);
    }

    /**
     * Parses the string as a local date.
     *
     * <p>The accepted text is {@code yyyy-MM-dd} with ASCII digits, with the year as
     * {@link LocalDate#toString()} writes it: exactly four digits and no sign for {@code 0000} to
     * {@code 9999}; otherwise a sign and no leading zeros beyond four digits ({@code +10000},
     * {@code -0001}, {@code -10000}). So {@code +2024}, {@code +02024}, {@code -00001} and
     * {@code -0000} are rejected. A date that does not exist, such as {@code 2023-02-29}, is
     * rejected.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link LocalDate}
     */
    public TemporalDecoder<I, LocalDate> date(@Nullable String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = TemporalText.date(value);
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_DATE,
                    message, "not a valid ISO-8601 date (e.g., 2024-01-15)", Map.of());
        }));
    }

    /**
     * Parses the string as a local time (e.g., {@code 10:30:00}).
     *
     * <p>See {@link #time(String)} for the accepted text.
     *
     * @return a temporal decoder producing {@link LocalTime}
     */
    public TemporalDecoder<I, LocalTime> time() {
        return time(null);
    }

    /**
     * Parses the string as a local time.
     *
     * <p>The accepted text is {@code HH:mm}, {@code HH:mm:ss}, or {@code HH:mm:ss} followed by
     * {@code .} and 1 to 9 fraction digits, as {@link LocalTime#toString()} writes it. Hours run
     * from {@code 00} to {@code 23}; {@code 24:00} and second {@code 60} are rejected.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link LocalTime}
     */
    public TemporalDecoder<I, LocalTime> time(@Nullable String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = TemporalText.time(value);
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_TIME,
                    message, "not a valid ISO-8601 local time (e.g., 10:30 or 10:30:45)", Map.of());
        }));
    }

    /**
     * Parses the string as a local date-time (e.g., {@code 2024-01-15T10:30:00}).
     *
     * <p>See {@link #dateTime(String)} for the accepted text.
     *
     * @return a temporal decoder producing {@link LocalDateTime}
     */
    public TemporalDecoder<I, LocalDateTime> dateTime() {
        return dateTime(null);
    }

    /**
     * Parses the string as a local date-time.
     *
     * <p>The accepted text is a date as in {@link #date(String)}, an upper-case {@code T}, and a
     * time as in {@link #time(String)} (e.g. {@code 2024-01-15T10:30},
     * {@code 2024-01-15T10:30:45.123}).
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link LocalDateTime}
     */
    public TemporalDecoder<I, LocalDateTime> dateTime(@Nullable String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = TemporalText.dateTime(value);
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_DATE_TIME,
                    message, "not a valid ISO-8601 local date-time (e.g., 2024-01-15T10:30 or 2024-01-15T10:30:45)", Map.of());
        }));
    }

    /**
     * Parses the string as an offset date-time (e.g., {@code 2024-01-15T10:30:00+09:00}).
     *
     * <p>See {@link #offsetDateTime(String)} for the accepted text.
     *
     * @return a temporal decoder producing {@link OffsetDateTime}
     */
    public TemporalDecoder<I, OffsetDateTime> offsetDateTime() {
        return offsetDateTime(null);
    }

    /**
     * Parses the string as an offset date-time.
     *
     * <p>The accepted text is a local date-time as in {@link #dateTime(String)} followed by an
     * offset: an upper-case {@code Z}, {@code ±HH:mm}, or {@code ±HH:mm:ss} (e.g.
     * {@code 2024-01-15T10:30+09:00}, {@code 2024-01-15T10:30:00Z}). Seconds may be omitted and
     * the offset may carry seconds, as {@link OffsetDateTime#toString()} writes them; an offset of
     * hours only ({@code +09}) is rejected. {@code +00:00} and {@code -00:00} are read as
     * {@code Z}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a temporal decoder producing {@link OffsetDateTime}
     */
    public TemporalDecoder<I, OffsetDateTime> offsetDateTime(@Nullable String message) {
        return new TemporalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            var parsed = TemporalText.offsetDateTime(value);
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return Result.failWith(path, ErrorCodes.INVALID_FORMAT, MessageKeys.INVALID_FORMAT_OFFSET_DATE_TIME,
                    message, "not a valid ISO-8601 offset date-time (e.g., 2024-01-15T10:30:00+09:00)", Map.of());
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
     * <p>The accepted text is an optional {@code +} or {@code -} followed by one or more ASCII
     * digits {@code 0}–{@code 9}; leading zeros are allowed ({@code +5}, {@code -0}, {@code 007}).
     * Other Unicode digits such as full-width {@code １２３} and spaces produce
     * {@code type_mismatch}; a value outside the {@code int} range produces {@code type_mismatch}
     * with the message key {@code type_mismatch.numeric_range}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return an integer decoder with the parsed value
     */
    public IntDecoder<I> toInt(@Nullable String message) {
        return new IntDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            if (!LexicalRules.isInteger(value)) {
                return Result.failWith(path, ErrorCodes.TYPE_MISMATCH, message, "expected integer",
                        Map.of("expected", "integer"));
            }
            try {
                return Result.ok(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                // The text is well-formed, so it failed for being outside the int range. Reported
                // like ObjectDecoders.int_() reports a number outside the range.
                return Result.failWith(path, ErrorCodes.TYPE_MISMATCH, MessageKeys.TYPE_MISMATCH_NUMERIC_RANGE,
                        message, "value is outside the integer range", Map.of("expected", "integer"));
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
     * <p>The accepted text is an optional {@code +} or {@code -} followed by one or more ASCII
     * digits {@code 0}–{@code 9}; leading zeros are allowed ({@code +5}, {@code -0}, {@code 007}).
     * Other Unicode digits such as full-width {@code １２３} and spaces produce
     * {@code type_mismatch}; a value outside the {@code long} range produces {@code type_mismatch}
     * with the message key {@code type_mismatch.numeric_range}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a long decoder with the parsed value
     */
    public LongDecoder<I> toLong(@Nullable String message) {
        return new LongDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            if (!LexicalRules.isInteger(value)) {
                return Result.failWith(path, ErrorCodes.TYPE_MISMATCH, message, "expected long",
                        Map.of("expected", "long"));
            }
            try {
                return Result.ok(Long.parseLong(value));
            } catch (NumberFormatException e) {
                // The text is well-formed, so it failed for being outside the long range. Reported
                // like ObjectDecoders.long_() reports a number outside the range.
                return Result.failWith(path, ErrorCodes.TYPE_MISMATCH, MessageKeys.TYPE_MISMATCH_NUMERIC_RANGE,
                        message, "value is outside the long range", Map.of("expected", "long"));
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
     * <p>The accepted text is an optional {@code +} or {@code -}, ASCII digits with an optional
     * decimal point that has a digit on at least one side ({@code 12}, {@code 12.5}, {@code 5.},
     * {@code .5}), and an optional exponent: {@code e} or {@code E} followed by an optionally
     * signed integer ({@code 1e3}, {@code 2.5E-4}). Other Unicode digits such as full-width
     * {@code １２}, spaces, {@code NaN} and {@code Infinity} produce {@code type_mismatch}, as does
     * an exponent that {@link BigDecimal} cannot represent. To restrict the form further, for
     * example to amounts without an exponent, apply {@link #pattern(String)} before this
     * conversion.
     *
     * <p>The value is the one {@link BigDecimal#BigDecimal(String)} gives, built by divide and
     * conquer instead of folding the digits in one group at a time, which takes time that grows
     * with the square of their number. It still grows faster than linearly, and the decoder sets no
     * limit of its own, since how many digits are valid is up to the caller. For input from an
     * untrusted source, bound the length first, as in {@code string().maxLength(40).toDecimal()}.
     *
     * @param message custom error message, or {@code null} for the default
     * @return a decimal decoder with the parsed value
     */
    public DecimalDecoder<I> toDecimal(@Nullable String message) {
        return new DecimalDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            // Null for text outside the form, and for an exponent that puts the scale outside the
            // int range, which no BigDecimal holds.
            var parsed = DecimalConversion.toBigDecimal(value);
            if (parsed != null) {
                return Result.ok(parsed);
            }
            return message != null
                    ? Result.failCustom(path, ErrorCodes.TYPE_MISMATCH, message,
                            Map.of("expected", "decimal"))
                    : Result.fail(path, ErrorCodes.TYPE_MISMATCH, "expected decimal",
                            Map.of("expected", "decimal"));
        }));
    }

    /**
     * Parses the string as a boolean.
     *
     * <p>Recognises common form-data representations (ASCII case-insensitive:
     * {@code A}-{@code Z} are equivalent to {@code a}-{@code z}, with no Unicode case mapping):</p>
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
    public BoolDecoder<I> toBool(@Nullable String message) {
        return new BoolDecoder<>((in, path) -> this.decode(in, path).flatMap(value -> {
            Boolean parsed = switch (asciiLowerCase(value)) {
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

    /** Lower-cases {@code A}-{@code Z} only; see {@code Decoders.enumOf}, which keeps its own copy. */
    private static String asciiLowerCase(String value) {
        int first = 0;
        while (first < value.length() && (value.charAt(first) < 'A' || value.charAt(first) > 'Z')) {
            first++;
        }
        if (first == value.length()) {
            return value;
        }
        var chars = value.toCharArray();
        for (int i = first; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] += 'a' - 'A';
            }
        }
        return new String(chars);
    }

    /**
     * The number of characters in {@code s}, counted in Unicode code points rather than the UTF-16
     * units a JVM {@code String} stores. A character outside the basic multilingual plane — a
     * supplementary-plane kanji such as {@code 𠮷}, an emoji — occupies two units and is one
     * character, so the two counts disagree exactly where a length limit on a name or a remarks
     * field would be surprising to whoever hits it.
     *
     * <p>This is not the grapheme cluster a reader counts: a base letter with a combining accent is
     * two code points, and an emoji joined with zero-width joiners is several. That unit depends on
     * Unicode segmentation and is not what a stored-length constraint is about.
     */
    private static int codePointLength(String s) {
        // Never more than s.length(), so it fits an int.
        return (int) ScalarValues.count(s);
    }


    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link StringDecoder} so that string constraints can still be chained
     * after the refinement.
     */
    @Override
    public StringDecoder<I> refine(Predicate<? super String> ok, String code, String message) {
        return refine(ok, code, message, v -> Map.of());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link StringDecoder} so that string constraints can still be chained
     * after the refinement.
     */
    @Override
    public StringDecoder<I> refine(Predicate<? super String> ok, String code, String message,
                                   Function<? super String, ? extends Map<String, Object>> metaFn) {
        return refine(ok, (v, path) -> Result.failCustom(path, code, message, metaFn.apply(v)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The return type is narrowed to {@link StringDecoder} so that string constraints can still be chained
     * after the refinement.
     */
    @Override
    public StringDecoder<I> refine(Predicate<? super String> ok,
                                   BiFunction<? super String, ? super Path, ? extends Result<String>> onFail) {
        return chain((value, path) -> ok.test(value) ? Result.ok(value) : onFail.apply(value, path));
    }

    private StringDecoder<I> chain(Decoder<String, String> constraint) {
        return new StringDecoder<>((in, path) ->
                this.decode(in, path).flatMap(value -> constraint.decode(value, path))
        );
    }
}
