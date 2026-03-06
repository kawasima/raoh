package net.unit8.raoh.json;

import net.unit8.raoh.*;
import net.unit8.raoh.combinator.*;
import net.unit8.raoh.builtin.*;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JsonDecoders {

    private JsonDecoders() {}

    // --- Primitive decoders ---

    public static StringDecoder<JsonNode> string() {
        Decoder<JsonNode, String> base = allowBlankBase();
        return new StringDecoder<>(base, base);
    }

    public static StringDecoder<JsonNode> allowBlankString() {
        return new StringDecoder<>(allowBlankBase());
    }

    private static Decoder<JsonNode, String> allowBlankBase() {
        return (in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isTextual()) {
                return Result.fail(path, "type_mismatch", "expected string",
                        Map.of("expected", "string", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.asText());
        };
    }

    public static IntDecoder<JsonNode> int_() {
        return new IntDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isInt() && !in.isLong() && !in.isShort()) {
                if (in.isNumber()) {
                    return Result.fail(path, "type_mismatch", "expected integer",
                            Map.of("expected", "integer"));
                }
                return Result.fail(path, "type_mismatch", "expected integer",
                        Map.of("expected", "integer", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.intValue());
        });
    }

    public static LongDecoder<JsonNode> long_() {
        return new LongDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isInt() && !in.isLong() && !in.isShort()) {
                return Result.fail(path, "type_mismatch", "expected long",
                        Map.of("expected", "long", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.longValue());
        });
    }

    public static BoolDecoder<JsonNode> bool() {
        return new BoolDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isBoolean()) {
                return Result.fail(path, "type_mismatch", "expected boolean",
                        Map.of("expected", "boolean", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.booleanValue());
        });
    }

    public static DecimalDecoder<JsonNode> decimal() {
        return new DecimalDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isNumber()) {
                return Result.fail(path, "type_mismatch", "expected number",
                        Map.of("expected", "number", "actual", in.getNodeType().name().toLowerCase()));
            }
            return Result.ok(in.decimalValue());
        });
    }

    // --- field / optionalField / nullable ---

    public static <T> Decoder<JsonNode, T> field(String name, Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.isObject()) {
                return Result.fail(fieldPath, "type_mismatch", "expected object",
                        Map.of("expected", "object"));
            }
            var node = in.get(name);
            if (node == null) {
                node = com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            }
            return dec.decode(node, fieldPath);
        };
    }

    public static <T> Decoder<JsonNode, Optional<T>> optionalField(String name, Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.isObject()) {
                return Result.ok(Optional.empty());
            }
            var node = in.get(name);
            if (node == null || node.isMissingNode()) {
                return Result.ok(Optional.empty());
            }
            return dec.decode(node, fieldPath).map(Optional::of);
        };
    }

    public static <T> Decoder<JsonNode, T> nullable(Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            if (in == null || in.isNull()) {
                return Result.ok(null);
            }
            return dec.decode(in, path);
        };
    }

    public static <T> Decoder<JsonNode, Presence<T>> optionalNullableField(String name, Decoder<JsonNode, T> dec) {
        return (in, path) -> {
            var fieldPath = path.append(name);
            if (in == null || !in.isObject()) {
                return Result.ok(new Presence.Absent<>());
            }
            var node = in.get(name);
            if (node == null || node.isMissingNode()) {
                return Result.ok(new Presence.Absent<>());
            }
            if (node.isNull()) {
                return Result.ok(new Presence.PresentNull<>());
            }
            return dec.decode(node, fieldPath).map(v -> (Presence<T>) new Presence.Present<>(v));
        };
    }

    // --- list / map ---

    public static <T> ListDecoder<JsonNode, T> list(Decoder<JsonNode, T> elementDec) {
        return new ListDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isArray()) {
                return Result.fail(path, "type_mismatch", "expected array",
                        Map.of("expected", "array", "actual", in.getNodeType().name().toLowerCase()));
            }
            var issues = Issues.EMPTY;
            var results = new ArrayList<T>();
            for (int i = 0; i < in.size(); i++) {
                var elemPath = path.append(String.valueOf(i));
                var r = elementDec.decode(in.get(i), elemPath);
                switch (r) {
                    case Ok<T> ok -> results.add(ok.value());
                    case Err<T> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.list().isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(List.copyOf(results));
        });
    }

    public static <V> RecordDecoder<JsonNode, V> map(Decoder<JsonNode, V> valDec) {
        return new RecordDecoder<>((in, path) -> {
            if (in == null || in.isNull() || in.isMissingNode()) {
                return Result.fail(path, "required", "is required");
            }
            if (!in.isObject()) {
                return Result.fail(path, "type_mismatch", "expected object",
                        Map.of("expected", "object", "actual", in.getNodeType().name().toLowerCase()));
            }
            var issues = Issues.EMPTY;
            var results = new LinkedHashMap<String, V>();
            var fields = in.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                var keyPath = path.append(entry.getKey());
                var r = valDec.decode(entry.getValue(), keyPath);
                switch (r) {
                    case Ok<V> ok -> results.put(entry.getKey(), ok.value());
                    case Err<V> err -> issues = issues.merge(err.issues());
                }
            }
            if (!issues.list().isEmpty()) {
                return Result.err(issues);
            }
            return Result.ok(Map.copyOf(results));
        });
    }

    // --- enumOf / literal ---

    public static <E extends Enum<E>> Decoder<JsonNode, E> enumOf(Class<E> cls) {
        return Decoders.enumOf(cls, allowBlankString());
    }

    public static Decoder<JsonNode, String> literal(String expected) {
        return Decoders.literal(expected, allowBlankString());
    }

    // --- discriminate ---

    public static <T> Decoder<JsonNode, T> discriminate(
            String fieldName,
            Map<String, Decoder<JsonNode, ? extends T>> variants) {
        return (in, path) -> {
            var tag = field(fieldName, allowBlankString()).decode(in, path);
            return switch (tag) {
                case Err<String> err -> err.coerce();
                case Ok<String> ok -> {
                    var dec = variants.get(ok.value());
                    if (dec == null) {
                        yield Result.fail(path.append(fieldName),
                                "invalid_format", "unknown type: " + ok.value(),
                                Map.of("allowed", variants.keySet()));
                    }
                    @SuppressWarnings("unchecked")
                    var result = (Result<T>) dec.decode(in, path);
                    yield result;
                }
            };
        };
    }

    // --- strict ---

    public static <T> Decoder<JsonNode, T> strict(Decoder<JsonNode, T> dec, Set<String> knownFields) {
        return Decoders.strict(dec, knownFields);
    }

    // --- Delegate combine to Decoders ---

    public static <A, B> Combiner2<JsonNode, A, B> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db) {
        return Decoders.combine(da, db);
    }

    public static <A, B, C> Combiner3<JsonNode, A, B, C> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc) {
        return Decoders.combine(da, db, dc);
    }

    public static <A, B, C, D> Combiner4<JsonNode, A, B, C, D> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd) {
        return Decoders.combine(da, db, dc, dd);
    }

    public static <A, B, C, D, E> Combiner5<JsonNode, A, B, C, D, E> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de) {
        return Decoders.combine(da, db, dc, dd, de);
    }

    public static <A, B, C, D, E, F> Combiner6<JsonNode, A, B, C, D, E, F> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df) {
        return Decoders.combine(da, db, dc, dd, de, df);
    }

    public static <A, B, C, D, E, F, G> Combiner7<JsonNode, A, B, C, D, E, F, G> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg) {
        return Decoders.combine(da, db, dc, dd, de, df, dg);
    }

    public static <A, B, C, D, E, F, G, H> Combiner8<JsonNode, A, B, C, D, E, F, G, H> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh);
    }

    public static <A, B, C, D, E, F, G, H, J> Combiner9<JsonNode, A, B, C, D, E, F, G, H, J> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj);
    }

    public static <A, B, C, D, E, F, G, H, J, K> Combiner10<JsonNode, A, B, C, D, E, F, G, H, J, K> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L> Combiner11<JsonNode, A, B, C, D, E, F, G, H, J, K, L> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M> Combiner12<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N> Combiner13<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O> Combiner14<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn, Decoder<JsonNode, O> do_) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> Combiner15<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn, Decoder<JsonNode, O> do_, Decoder<JsonNode, P> dp) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp);
    }

    public static <A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> Combiner16<JsonNode, A, B, C, D, E, F, G, H, J, K, L, M, N, O, P, Q> combine(
            Decoder<JsonNode, A> da, Decoder<JsonNode, B> db, Decoder<JsonNode, C> dc,
            Decoder<JsonNode, D> dd, Decoder<JsonNode, E> de, Decoder<JsonNode, F> df,
            Decoder<JsonNode, G> dg, Decoder<JsonNode, H> dh, Decoder<JsonNode, J> dj,
            Decoder<JsonNode, K> dk, Decoder<JsonNode, L> dl, Decoder<JsonNode, M> dm,
            Decoder<JsonNode, N> dn, Decoder<JsonNode, O> do_, Decoder<JsonNode, P> dp,
            Decoder<JsonNode, Q> dq) {
        return Decoders.combine(da, db, dc, dd, de, df, dg, dh, dj, dk, dl, dm, dn, do_, dp, dq);
    }
}
