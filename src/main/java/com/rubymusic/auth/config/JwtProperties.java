package com.rubymusic.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Base64-encoded PKCS#8 RSA private key (strip PEM headers before encoding). */
    private String privateKey;

    /** Base64-encoded X.509 RSA public key (strip PEM headers before encoding). */
    private String publicKey;

    /** Access token validity in milliseconds. Default: 15 minutes. */
    private long accessTokenExpirationMs = 900_000L;

    /** Refresh token validity in days. Default: 30 days. */
    private int refreshTokenExpirationDays = 30;
}
