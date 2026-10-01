package net.unit8.raoh;

import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.Decoders;
import net.unit8.raoh.internal.CandidateFailures;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.unit8.raoh.decode.ObjectDecoders.int_;
import static net.unit8.raoh.decode.ObjectDecoders.map;
import static net.unit8.raoh.decode.ObjectDecoders.string;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolving, rebasing and serializing reach the issues a {@code one_of_failed} issue holds for its
 * candidates, keep what they are, and do it without the call stack (#167).
 */
class IssueTreeTest {

    private static final ResourceBundleMessageResolver BUNDLE =
            new ResourceBundleMessageResolver("net.unit8.raoh.messages");

    private static Issue only(Result<?> result) {
        List<Issue> issues = assertInstanceOf(Err.class, result).issues().asList();
        assertEquals(1, issues.size(), issues.toString());
        return issues.get(0);
    }

    /** The issues of candidate {@code index}, as the {@code candidates} metadata reads. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> candidate(Issue oneOfFailed, int index) {
        var candidates = (List<Map<String, Object>>) oneOfFailed.meta().get("candidates");
        return (List<Map<String, Object>>) candidates.get(index).get("issues");
    }

    private static String message(Map<String, Object> serialized) {
        return (String) serialized.get("message");
    }

    // --- resolve ---

    @Test
    void resolveReachesTheCandidates() {
        Decoder<Object, Object> dec = Decoders.oneOf(map(int_()).minSize(2), int_());
        Issue failed = only(dec.decode(Map.of("a", 1), Path.ROOT));

        assertEquals("must have at least 2 elements", message(candidate(failed, 0).get(0)));
        Issue english = failed.resolve(MessageResolver.DEFAULT);
        assertEquals("must have at least 2 elements", message(candidate(english, 0).get(0)));

        Issue japanese = failed.resolve(BUNDLE, Locale.JAPANESE);
        assertEquals("いずれのバリアントにも一致しませんでした", japanese.message());
        assertEquals(BUNDLE.resolve(only(map(int_()).minSize(2).decode(Map.of("a", 1), Path.ROOT)), Locale.JAPANESE),
                message(candidate(japanese, 0).get(0)));
        assertNotEquals("must have at least 2 elements", message(candidate(japanese, 0).get(0)));
    }

    @Test
    void resolveUsesEachCandidateIssuesMessageKey() {
        // email() reports invalid_format with the key invalid_format.email; resolving by the code
        // alone would give the generic invalid_format sentence.
        Issue failed = only(Decoders.oneOf(string().email(), int_()).decode("nope", Path.ROOT));
        Issue japanese = failed.resolve(BUNDLE, Locale.JAPANESE);
        assertEquals("メールアドレスの形式が不正です", message(candidate(japanese, 0).get(0)));
    }

    @Test
    void aCustomMessageInACandidateIsKept() {
        Issue failed = only(Decoders.oneOf(map(int_()).minSize(2, "need two"), int_()).decode(Map.of("a", 1), Path.ROOT));
        Issue japanese = failed.resolve(BUNDLE, Locale.JAPANESE);
        assertEquals("need two", message(candidate(japanese, 0).get(0)));
    }

    @Test
    void nestedCandidatesAreResolvedAtEveryLevel() {
        Decoder<Object, Object> inner = Decoders.oneOf(string().email(), map(int_()).minSize(2));
        Issue failed = only(Decoders.oneOf(inner, int_()).decode("nope", Path.ROOT)).resolve(BUNDLE, Locale.JAPANESE);
        Map<String, Object> innerFailed = candidate(failed, 0).get(0);
        assertEquals("いずれのバリアントにも一致しませんでした", message(innerFailed));
        @SuppressWarnings("unchecked")
        var innerCandidates = (List<Map<String, Object>>) ((Map<String, Object>) innerFailed.get("meta")).get("candidates");
        @SuppressWarnings("unchecked")
        var emailIssues = (List<Map<String, Object>>) innerCandidates.get(0).get("issues");
        assertEquals("メールアドレスの形式が不正です", message(emailIssues.get(0)));
    }

    @Test
    void aCustomParentStillHasItsCandidatesResolved() {
        Issue failed = only(Decoders.oneOf(map(int_()).minSize(2), int_()).decode(Map.of("a", 1), Path.ROOT))
                .withCustomMessage("pick one");
        Issue japanese = failed.resolve(BUNDLE, Locale.JAPANESE);
        assertEquals("pick one", japanese.message());
        assertNotEquals("must have at least 2 elements", message(candidate(japanese, 0).get(0)));
    }

    @Test
    void theResolverSeesTheCandidatesAlreadyResolved() {
        Issue failed = only(Decoders.oneOf(map(int_()).minSize(2), int_()).decode(Map.of("a", 1), Path.ROOT));
        var seen = new ArrayList<String>();
        MessageResolver spy = (code, meta) -> {
            if (code.equals(ErrorCodes.ONE_OF_FAILED)) {
                @SuppressWarnings("unchecked")
                var candidates = (List<Map<String, Object>>) meta.get("candidates");
                @SuppressWarnings("unchecked")
                var issues = (List<Map<String, Object>>) candidates.get(0).get("issues");
                seen.add(message(issues.get(0)));
                return "none matched";
            }
            return "resolved " + code;
        };
        assertEquals("none matched", failed.resolve(spy).message());
        assertEquals(List.of("resolved too_small"), seen);
    }

    // --- rebase ---

    @Test
    void rebaseReachesTheCandidates() {
        // The way flatMap rebases: the candidates ran at the root of a value under /order.
        Issue failed = only(Decoders.oneOf(map(int_()).minSize(2), int_()).decode(Map.of("a", 1), Path.ROOT));
        Issue rebased = failed.rebase(Path.of("order"));
        assertEquals("/order", rebased.path().toJsonPointer());
        assertEquals("/order", candidate(rebased, 0).get(0).get("path"));
        assertEquals("/order", candidate(rebased, 1).get(0).get("path"));

        Issues viaIssues = new Issues(List.of(failed)).rebase(Path.of("order"));
        assertEquals("/order", candidate(viaIssues.asList().get(0), 0).get(0).get("path"));
    }

    @Test
    void rebasesComposeOutermostFirst() {
        Issue failed = only(Decoders.oneOf(map(int_()).minSize(2), int_()).decode(Map.of("a", 1), Path.of("x")));
        Issue twice = failed.rebase(Path.of("inner")).rebase(Path.of("outer"));
        assertEquals("/outer/inner/x", twice.path().toJsonPointer());
        assertEquals("/outer/inner/x", candidate(twice, 0).get(0).get("path"));
        // Resolving and serializing after a rebase see the rebased paths.
        Issue resolved = twice.resolve(BUNDLE, Locale.JAPANESE);
        assertEquals("/outer/inner/x", candidate(resolved, 0).get(0).get("path"));
        @SuppressWarnings("unchecked")
        var serialized = (List<Map<String, Object>>) ((List<Map<String, Object>>) ((Map<String, Object>)
                new Issues(List.of(twice)).toJsonList().get(0).get("meta")).get("candidates")).get(0).get("issues");
        assertEquals("/outer/inner/x", serialized.get(0).get("path"));
    }

    @Test
    void rebasingDoesNotRebuildTheTreeBelow() {
        // A failure DEPTH levels deep passed up LEVELS times, each rebasing it, as nested flatMaps
        // do. Rebuilding the tree below each time would copy LEVELS × DEPTH issues; recording the
        // prefix copies none of them. The limit is far above the second and far below the first.
        int levels = 1_000;
        Issues tree = deep();
        Issues rebased = assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            Issues current = tree;
            for (int i = 0; i < levels; i++) {
                current = current.rebase(Path.of("l"));
            }
            return current;
        });
        assertEquals("/" + "l/".repeat(levels - 1) + "l", bottom(rebased).path().toJsonPointer());
    }

    @Test
    void flatMapRebasesTheCandidatesOfAOneOfItRuns() {
        Decoder<Object, Object> inner = Decoders.oneOf(map(int_()).minSize(2), int_());
        Decoder<Object, Object> outer = (in, path) -> Result.ok(in);
        Decoder<Object, Object> dec = outer.flatMap(v -> inner.decode(v, Path.ROOT));
        Issue failed = only(dec.decode(Map.of("a", 1), Path.of("items", "0")));
        assertEquals("/items/0", failed.path().toJsonPointer());
        assertEquals("/items/0", candidate(failed, 0).get(0).get("path"));
    }

    // --- the metadata and the serialized form ---

    @Test
    void theCandidatesReadAsBefore() {
        Issue failed = only(Decoders.oneOf(map(int_()).minSize(2), int_()).decode(Map.of("a", 1), Path.ROOT));
        var candidates = assertInstanceOf(List.class, failed.meta().get("candidates"));
        assertEquals(2, candidates.size());
        var first = assertInstanceOf(Map.class, candidates.get(0));
        assertEquals(Set_of("candidate", "issues"), first.keySet());
        assertEquals(0, first.get("candidate"));
        var issues = assertInstanceOf(List.class, first.get("issues"));
        var issue = assertInstanceOf(Map.class, issues.get(0));
        assertEquals(Set_of("path", "code", "message", "meta"), issue.keySet());
        assertEquals("too_small", issue.get("code"));
    }

    private static java.util.Set<String> Set_of(String... names) {
        return java.util.Set.of(names);
    }

    @Test
    void theSerializedFormHoldsNoRaohType() {
        Decoder<Object, Object> inner = Decoders.oneOf(string().email(), map(int_()).minSize(2));
        Issues issues = assertInstanceOf(Err.class, Decoders.oneOf(inner, int_()).decode("nope", Path.ROOT)).issues();
        var pending = new ArrayDeque<Object>(List.of(issues.toJsonList()));
        int seen = 0;
        while (!pending.isEmpty()) {
            Object value = pending.pop();
            seen++;
            assertFalse(value.getClass().getName().startsWith("net.unit8.raoh"), value.getClass().getName());
            if (value instanceof Map<?, ?> map) {
                assertFalse(map.containsKey("messageKey"));
                pending.addAll(map.values());
            } else if (value instanceof List<?> list) {
                pending.addAll(list);
            }
        }
        assertTrue(seen > 20, "walked " + seen);
    }

    @Test
    void equalityIsThatOfTheListAsRead() {
        Issues a = new Issues(List.of(Issue.of(Path.ROOT, "too_small", "too_small", "m", Map.of())));
        Issues keyed = new Issues(List.of(Issue.of(Path.ROOT, "too_small", "too_small.other", "m", Map.of())));
        Issues custom = new Issues(List.of(new Issue(Path.ROOT, "too_small", "m", Map.of(), true)));
        var failures = new CandidateFailures(List.of(a));
        List<Map<String, Object>> plain = List.of(Map.of("candidate", 0, "issues", a.toJsonList()));

        assertEquals(plain, failures);
        assertEquals(failures, plain);
        assertEquals(plain.hashCode(), failures.hashCode());
        assertEquals(failures, new CandidateFailures(List.of(keyed)));
        assertEquals(failures, new CandidateFailures(List.of(custom)));
        assertEquals(failures.hashCode(), new CandidateFailures(List.of(keyed)).hashCode());
    }

    // --- depth ---

    private static final int DEPTH = 10_000;

    /** A one_of_failed issue whose only candidate failed with one, DEPTH levels down to a map size issue. */
    private static Issues deep() {
        Issues current = assertInstanceOf(Err.class, map(int_()).minSize(2).decode(Map.of("a", 1), Path.ROOT)).issues();
        for (int i = 0; i < DEPTH; i++) {
            current = new Issues(List.of(Issue.of(Path.ROOT, ErrorCodes.ONE_OF_FAILED, "no variant matched",
                    Map.of("candidates", new CandidateFailures(List.of(current))))));
        }
        return current;
    }

    /** The issue at the bottom, reached through the issues each level holds. */
    private static Issue bottom(Issues top) {
        Issues current = top;
        for (int i = 0; i < DEPTH; i++) {
            var failures = assertInstanceOf(CandidateFailures.class, current.asList().get(0).meta().get("candidates"));
            current = failures.children().get(0);
        }
        return current.asList().get(0);
    }

    /** The issue at the bottom of a serialized tree, as a map. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> bottom(List<Map<String, Object>> serialized) {
        List<Map<String, Object>> current = serialized;
        for (int i = 0; i < DEPTH; i++) {
            var meta = (Map<String, Object>) current.get(0).get("meta");
            var candidates = (List<Map<String, Object>>) meta.get("candidates");
            current = (List<Map<String, Object>>) candidates.get(0).get("issues");
        }
        return current.get(0);
    }

    @Test
    void aDeepTreeIsWalkedWithoutTheCallStack() {
        Issues tree = deep();
        String japanese = BUNDLE.resolve(bottom(tree), Locale.JAPANESE);

        assertEquals("must have at least 2 elements", bottom(tree.resolve(MessageResolver.DEFAULT)).message());
        assertTrue(bottom(tree.resolve(MessageResolver.DEFAULT)).customMessage(), "resolved");
        assertEquals(japanese, bottom(tree.resolve(BUNDLE, Locale.JAPANESE)).message());
        assertEquals("/deep", bottom(tree.rebase(Path.of("deep"))).path().toJsonPointer());

        Map<String, Object> serialized = bottom(tree.toJsonList());
        assertEquals("must have at least 2 elements", serialized.get("message"));
        assertEquals("", serialized.get("path"));

        // The metadata reads the same way from the top.
        @SuppressWarnings("unchecked")
        var candidates = (List<Map<String, Object>>) tree.asList().get(0).meta().get("candidates");
        @SuppressWarnings("unchecked")
        var below = (List<Map<String, Object>>) candidates.get(0).get("issues");
        List<Map<String, Object>> fromMeta = new ArrayList<>(List.of(Map.of("meta", Map.of("candidates", List.of(Map.of("issues", below))))));
        assertEquals("must have at least 2 elements", bottom(fromMeta).get("message"));
    }
}
