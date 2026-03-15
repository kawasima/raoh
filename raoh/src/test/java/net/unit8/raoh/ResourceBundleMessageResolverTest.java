package net.unit8.raoh;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceBundleMessageResolverTest {

    private static final ResourceBundleMessageResolver resolver =
            new ResourceBundleMessageResolver("net.unit8.raoh.test_messages");

    // --- Basic resolution ---

    @Test
    void resolvesDefaultEnglishMessage() {
        var result = resolver.resolve("required", Map.of(), Locale.ENGLISH);
        assertEquals("is required", result);
    }

    @Test
    void resolvesJapaneseMessage() {
        var result = resolver.resolve("required", Map.of(), Locale.JAPANESE);
        assertEquals("必須です", result);
    }

    // --- Placeholder substitution ---

    @Test
    void replacesNamedPlaceholders() {
        var meta = Map.<String, Object>of("min", 5);
        var result = resolver.resolve("too_short", meta, Locale.ENGLISH);
        assertEquals("must be at least 5 characters", result);
    }

    @Test
    void replacesNamedPlaceholdersInJapanese() {
        var meta = Map.<String, Object>of("min", 3);
        var result = resolver.resolve("too_short", meta, Locale.JAPANESE);
        assertEquals("3文字以上で入力してください", result);
    }

    @Test
    void replacesMultiplePlaceholders() {
        var meta = Map.<String, Object>of("min", 0, "max", 150);
        var result = resolver.resolve("out_of_range", meta, Locale.JAPANESE);
        assertEquals("0から150の範囲で入力してください", result);
    }

    // --- Fallback to MessageResolver.DEFAULT ---

    @Test
    void fallsBackToDefaultForUnknownCode() {
        var result = resolver.resolve("unknown_field", Map.of(), Locale.ENGLISH);
        assertEquals("unknown field", result);
    }

    // --- Integration with Issues.resolve(resolver, locale) ---

    @Test
    void issuesResolveWithLocale() {
        var issues = new Issues(List.of(
                Issue.of(Path.ROOT.append("name"), "required", "is required"),
                Issue.of(Path.ROOT.append("age"), "out_of_range", "out of range",
                        Map.of("min", 0, "max", 150))
        ));

        var resolved = issues.resolve(resolver, Locale.JAPANESE);
        var flat = resolved.flatten();

        assertEquals(List.of("必須です"), flat.get("/name"));
        assertEquals(List.of("0から150の範囲で入力してください"), flat.get("/age"));
    }

    @Test
    void issuesResolveWithoutLocaleStillWorks() {
        var issues = new Issues(List.of(
                Issue.of(Path.ROOT.append("name"), "required", "is required")
        ));

        var resolved = issues.resolve(MessageResolver.DEFAULT);
        assertEquals("is required", resolved.asList().getFirst().message());
    }

    // --- customMessage is not overridden ---

    // --- Default bundle (net.unit8.raoh.messages) with Japanese locale ---

    private static final ResourceBundleMessageResolver defaultResolver =
            new ResourceBundleMessageResolver("net.unit8.raoh.messages");

    @Test
    void defaultBundleResolvesJapanese() {
        var result = defaultResolver.resolve("required", Map.of(), Locale.JAPANESE);
        assertEquals("必須です", result);
    }

    @Test
    void defaultBundleResolvesJapaneseWithPlaceholders() {
        var result = defaultResolver.resolve("too_short", Map.of("min", 5), Locale.JAPANESE);
        assertEquals("5文字以上で入力してください", result);
    }

    @Test
    void defaultBundleResolvesJapaneseNotAllowed() {
        var result = defaultResolver.resolve("not_allowed",
                Map.of("allowed", List.of("a", "b")), Locale.JAPANESE);
        assertEquals("[a, b]のいずれかで入力してください", result);
    }

    @Test
    void defaultBundleResolvesJapaneseUnknownField() {
        var result = defaultResolver.resolve("unknown_field", Map.of(), Locale.JAPANESE);
        assertEquals("不明なフィールドです", result);
    }

    // --- customMessage is not overridden ---

    @Test
    void customMessageNotOverridden() {
        var issue = Issue.of(Path.ROOT, "required", "original")
                .withCustomMessage("カスタムメッセージ");

        var resolved = issue.resolve(resolver, Locale.JAPANESE);
        assertEquals("カスタムメッセージ", resolved.message());
    }
}
