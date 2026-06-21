package com.elyadata.feature.scraper.site.dns;

import com.elyadata.feature.scraper.site.model.SiteDnsProbe;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;


@Slf4j
@ApplicationScoped
public class DnsResolver {

    private static final String INITIAL_TIMEOUT_MS = "600";
    private static final String RETRIES = "1";

    public SiteDnsProbe resolve(String host) {
        if (host == null || host.isBlank()) {
            log.warn("[DNS] resolve called with blank host — returning empty probe");
            return SiteDnsProbe.empty(host);
        }

        log.info("[DNS] ▶ resolving host={}", host);
        long start = System.nanoTime();

        long ipsStart = System.nanoTime();
        List<InetAddress> ips = resolveIps(host);
        long ipsMs = (System.nanoTime() - ipsStart) / 1_000_000;

        long nsStart = System.nanoTime();
        List<String> ns = resolveNs(host);
        long nsMs = (System.nanoTime() - nsStart) / 1_000_000;

        long totalMs = (System.nanoTime() - start) / 1_000_000;

        log.info("[DNS] ◀ host={} done in {}ms (A/AAAA={}ms, NS={}ms) — ips={} ns={}",
                host, totalMs, ipsMs, nsMs,
                ips.stream().map(InetAddress::getHostAddress).toList(),
                ns);

        return new SiteDnsProbe(host, ips, ns);
    }

    private List<InetAddress> resolveIps(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            List<InetAddress> list = Arrays.asList(addresses);
            log.debug("[DNS] A/AAAA for {} → {} address(es): {}",
                    host, list.size(),
                    list.stream().map(InetAddress::getHostAddress).toList());
            return list;
        } catch (UnknownHostException e) {
            log.warn("[DNS] A/AAAA lookup failed for {}: {}", host, e.getMessage());
            return List.of();
        }
    }

    private List<String> resolveNs(String host) {
        log.debug("[DNS] NS query at host level: {}", host);
        List<String> direct = queryNs(host);
        if (!direct.isEmpty()) {
            log.debug("[DNS] NS resolved at {}: {}", host, direct);
            return direct;
        }

        int firstDot = host.indexOf('.');
        if (firstDot > 0 && firstDot < host.length() - 1) {
            String parent = host.substring(firstDot + 1);
            log.debug("[DNS] no NS at {}, retrying at parent zone {}", host, parent);
            List<String> parentNs = queryNs(parent);
            if (parentNs.isEmpty()) {
                log.debug("[DNS] no NS at parent zone {} either", parent);
            } else {
                log.debug("[DNS] NS resolved at parent {}: {}", parent, parentNs);
            }
            return parentNs;
        }
        return List.of();
    }

    private List<String> queryNs(String host) {
        Map<String, String> env = new HashMap<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", INITIAL_TIMEOUT_MS);
        env.put("com.sun.jndi.dns.timeout.retries", RETRIES);


        InitialDirContext ctx = null;
        try {
            ctx = new InitialDirContext(new java.util.Hashtable<>(env));
            Attributes attrs = ctx.getAttributes(host, new String[]{"NS"});
            Attribute nsAttr = attrs.get("NS");
            if (nsAttr == null) {
                log.debug("[DNS] NS attribute null for {} (no records returned)", host);
                return List.of();
            }

            List<String> servers = new ArrayList<>();
            NamingEnumeration<?> values = nsAttr.getAll();
            while (values.hasMore()) {
                String ns = values.next().toString().toLowerCase(Locale.ROOT);
                if (ns.endsWith(".")) ns = ns.substring(0, ns.length() - 1);
                servers.add(ns);
            }
            return servers;
        } catch (NamingException e) {
            log.warn("[DNS] NS lookup failed for {} ({}): {}",
                    host, e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // no-op
                }
            }
        }
    }
}