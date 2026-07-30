package com.elyadata.feature.scraper.site.dns;

import com.elyadata.feature.scraper.site.model.OriginIpCandidate.ValidationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

@Slf4j
@ApplicationScoped
public class OriginIpValidator {

    @ConfigProperty(name = "dns-history.validator.timeout-ms", defaultValue = "5000")
    int timeoutMs;

    public ValidationResult validate(String ip, String domain) {
        long start = System.currentTimeMillis();
        try (Socket plain = new Socket()) {
            plain.connect(new InetSocketAddress(ip, 443), timeoutMs);
            plain.setSoTimeout(timeoutMs);

            SSLSocket tls = wrapTls(plain, domain);
            tls.startHandshake();

            sendMinimalRequest(tls, domain);
            int status = readStatusLine(tls);

            int latency = (int) (System.currentTimeMillis() - start);

            if (status >= 200 && status < 500) {
                log.debug("[ORIGIN-VALIDATE] {} → {} OK ({} in {}ms)", domain, ip, status, latency);
                return new ValidationResult(ValidationStatus.REACHABLE, status, latency);
            }
            return new ValidationResult(ValidationStatus.WRONG_CONTENT, status, latency);

        } catch (SSLException ssl) {
            return new ValidationResult(ValidationStatus.TLS_ERROR, null, latency(start));
        } catch (java.net.SocketTimeoutException to) {
            return new ValidationResult(ValidationStatus.TIMEOUT, null, latency(start));
        } catch (IOException e) {
            log.trace("[ORIGIN-VALIDATE] {} → {} unreachable: {}", domain, ip, e.getMessage());
            return new ValidationResult(ValidationStatus.UNREACHABLE, null, latency(start));
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("[ORIGIN-VALIDATE] TLS context init failed for {} → {}: {}", domain, ip, e.getMessage());
            return new ValidationResult(ValidationStatus.UNREACHABLE, null, latency(start));
        }
    }


    @SuppressWarnings("java:S4830") // Permissive trust is intentional — see Javadoc above
    private SSLSocket wrapTls(Socket plain, String domain)
            throws NoSuchAlgorithmException, KeyManagementException, IOException {

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new PermissiveTrustManager()}, new SecureRandom());

        SSLSocketFactory factory = ctx.getSocketFactory();
        SSLSocket tls = (SSLSocket) factory.createSocket(plain, domain, 443, true);

        SSLParameters params = tls.getSSLParameters();
        params.setServerNames(List.of(new SNIHostName(domain)));
        tls.setSSLParameters(params);
        tls.setUseClientMode(true);
        return tls;
    }

    private void sendMinimalRequest(SSLSocket tls, String domain) throws IOException {
        OutputStream out = tls.getOutputStream();
        String req = "HEAD / HTTP/1.1\r\n"
                + "Host: " + domain + "\r\n"
                + "User-Agent: ElyaScraper/1.0\r\n"
                + "Accept: */*\r\n"
                + "Connection: close\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private int readStatusLine(SSLSocket tls) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(tls.getInputStream()));
        String line = reader.readLine();
        if (line == null) return -1;
        String[] parts = line.split(" ");
        return parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
    }

    private static int latency(long start) {
        return (int) (System.currentTimeMillis() - start);
    }

    public record ValidationResult(ValidationStatus status, Integer httpStatus, Integer latencyMs) {
    }


    @SuppressWarnings("java:S4830") // Permissive trust is intentional — see Javadoc above
    private static final class PermissiveTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Intentionally empty — client certificate validation is not required
            // for outbound origin-IP probing (this validator acts as the TLS client)
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Intentionally empty — server certificate is not validated because
            // origin IPs may present a CDN certificate unrelated to the target domain
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}