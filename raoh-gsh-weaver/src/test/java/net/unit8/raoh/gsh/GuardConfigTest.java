package net.unit8.raoh.gsh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuardConfigTest {

    @Test
    void parseSinglePackage() {
        GuardConfig config = GuardConfig.parse("packages=com.example.domain.**");
        assertTrue(config.isTarget("com.example.domain.Email"));
        assertTrue(config.isTarget("com.example.domain.sub.UserId"));
        assertFalse(config.isTarget("com.example.service.UserService"));
    }

    @Test
    void parseMultiplePackages() {
        GuardConfig config = GuardConfig.parse("packages=com.example.domain.**,com.example.model.*");
        assertTrue(config.isTarget("com.example.domain.Email"));
        assertTrue(config.isTarget("com.example.model.Foo"));
        assertFalse(config.isTarget("com.example.model.sub.Bar"));
    }

    @Test
    void parseExplicitClasses() {
        GuardConfig config = GuardConfig.parse("classes=com.example.Money,com.example.Currency");
        assertTrue(config.isTarget("com.example.Money"));
        assertTrue(config.isTarget("com.example.Currency"));
        assertFalse(config.isTarget("com.example.Other"));
    }

    @Test
    void excludeTakesPrecedence() {
        GuardConfig config = GuardConfig.parse(
                "packages=com.example.domain.**;exclude=com.example.domain.internal.**");
        assertTrue(config.isTarget("com.example.domain.Email"));
        assertFalse(config.isTarget("com.example.domain.internal.Helper"));
    }

    @Test
    void isTargetInternalConvertsSeparators() {
        GuardConfig config = GuardConfig.parse("packages=com.example.domain.**");
        assertTrue(config.isTargetInternal("com/example/domain/Email"));
        assertFalse(config.isTargetInternal("com/other/Foo"));
    }

    @Test
    void parseNullReturnsEmptyConfig() {
        GuardConfig config = GuardConfig.parse(null);
        assertFalse(config.isTarget("anything"));
    }

    @Test
    void parseBlankReturnsEmptyConfig() {
        GuardConfig config = GuardConfig.parse("");
        assertFalse(config.isTarget("anything"));
    }

    @Test
    void singleStarMatchesSingleSegment() {
        GuardConfig config = GuardConfig.parse("packages=com.example.*");
        assertTrue(config.isTarget("com.example.Foo"));
        assertFalse(config.isTarget("com.example.sub.Bar"));
    }

    @Test
    void combinedAgentArgs() {
        GuardConfig config = GuardConfig.parse(
                "packages=com.example.domain.**;classes=com.special.Money;exclude=com.example.domain.dto.**");
        assertTrue(config.isTarget("com.example.domain.Email"));
        assertTrue(config.isTarget("com.special.Money"));
        assertFalse(config.isTarget("com.example.domain.dto.UserDto"));
        assertFalse(config.isTarget("com.other.Thing"));
    }

    @Test
    void doubleStarInMiddleMatchesAnySegments() {
        GuardConfig config = GuardConfig.parse("packages=com.**.domain.*");
        assertTrue(config.isTarget("com.example.domain.Email"));
        assertTrue(config.isTarget("com.foo.bar.domain.User"));
        assertTrue(config.isTarget("com.domain.Foo"));
        assertFalse(config.isTarget("com.example.domain.sub.Bar"));
        assertFalse(config.isTarget("org.example.domain.Email"));
    }
}
