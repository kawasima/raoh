package net.unit8.raoh;

import net.unit8.raoh.testing.PublicVarargsOverloads;
import org.junit.jupiter.api.Test;

/** No public method of raoh can have its varargs calls taken over by an overload. */
class PublicVarargsOverloadCompatibilityTest {

    @Test
    void noFixedArityOverloadCanCaptureAVarargsCall() {
        PublicVarargsOverloads.assertNoneCapturable(Result.class);
    }
}
