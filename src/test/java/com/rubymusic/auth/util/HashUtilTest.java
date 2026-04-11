package com.rubymusic.auth.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashUtilTest {

    @Test
    void sha256_knownVector_test() {
        assertThat(HashUtil.sha256("test"))
                .isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    void sha256_emptyString_returnsKnownHash() {
        assertThat(HashUtil.sha256(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256_nullInput_throwsNullPointerException() {
        assertThatThrownBy(() -> HashUtil.sha256(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sha256_returnLowercaseHex() {
        String hash = HashUtil.sha256("Hello");
        assertThat(hash).isLowerCase();
        assertThat(hash).hasSize(64);
    }
}
