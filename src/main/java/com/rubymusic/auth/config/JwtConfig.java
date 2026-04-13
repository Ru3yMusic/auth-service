package com.rubymusic.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA key pair used for JWT signing (RS256).
 *
 * <p>Set environment variables before running:
 * <pre>
 *   # Generate key pair (run once):
 *   openssl genrsa -out private.pem 2048
 *   openssl pkcs8 -topk8 -nocrypt -in private.pem -out private-pkcs8.pem
 *   openssl rsa  -in private.pem -pubout -out public.pem
 *
 *   # Export as Base64 (strip PEM headers):
 *   export JWT_PRIVATE_KEY=$(grep -v "PRIVATE" private-pkcs8.pem | tr -d '\n')
 *   export JWT_PUBLIC_KEY=$(grep  -v "PUBLIC"  public.pem       | tr -d '\n')
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    @ConditionalOnMissingBean(PrivateKey.class)
    public PrivateKey jwtPrivateKey(JwtProperties props) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPem(props.getPrivateKey()));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    @Bean
    @ConditionalOnMissingBean(PublicKey.class)
    public PublicKey jwtPublicKey(JwtProperties props) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPem(props.getPublicKey()));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    /** Removes PEM header/footer lines and whitespace so raw Base64 can be decoded. */
    private String stripPem(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
    }
}
