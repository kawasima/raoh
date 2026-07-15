package net.unit8.raoh.examples.spring.membership;

import net.unit8.raoh.encode.Encoder;

import org.jspecify.annotations.Nullable;

import java.util.Map;

import static net.unit8.raoh.encode.MapEncoders.*;
import static net.unit8.raoh.encode.ObjectEncoders.*;

/**
 * Map boundary encoders for the membership domain — the write-side mirror of
 * {@link MapMembershipDecoders}.
 *
 * <p>Where {@link MapMembershipDecoders#USER_ROW} decodes a {@code Map<String, Object>} JDBC row
 * into a {@link User}, {@link #USER_ROW} here encodes a {@link User} back into the same flat column
 * map, ready to bind to an {@code INSERT} / {@code UPDATE} statement. The two are inverses: decoding
 * an encoded row (or encoding a decoded row) yields the original value.
 *
 * <p>Use {@code import static net.unit8.raoh.examples.spring.membership.MapMembershipEncoders.*;}
 * to bring all constants into scope.
 */
public final class MapMembershipEncoders {

    private MapMembershipEncoders() {}

    /**
     * Encodes a {@link User} into a flat column map ({@code id}, {@code name}, {@code email}),
     * the exact inverse of {@link MapMembershipDecoders#USER_ROW}.
     *
     * <p>{@code contramap} unwraps each value object before encoding: {@code UserId -> long} and
     * {@code EmailAddress -> String}, matching how the decoder's {@code map(...)} wraps them.
     */
    public static final Encoder<User, Map<String, @Nullable Object>> USER_ROW = object(
            property("id",    User::id,    long_().contramap(UserId::value)),
            property("name",  User::name,  string()),
            property("email", User::email, string().contramap(EmailAddress::value)));
}
