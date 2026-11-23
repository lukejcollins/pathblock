package arpa.homelab.pathblock;

import android.net.Uri;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Locale;

final class Rule {
    record Address(String host, int port, String path) {
        Address {
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (host.startsWith("www.")) host = host.substring(4);
            int end = path.length();
            while (end > 1 && path.charAt(end - 1) == '/') end--;
            path = path.substring(0, end).toLowerCase(Locale.ROOT);
        }

        String label() {
            return host + (port == -1 ? "" : ":" + port) + path;
        }
    }

    static Address parse(String text) {
        if (text == null || text.isBlank()) return null;
        String input = text.strip();
        if (!input.contains("://")) input = "https://" + input;
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme();
            if ((!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) return null;
            return new Address(uri.getHost(), uri.getPort(), normalizePath(uri.getRawPath()));
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isEmpty()) return "/";
        String path = Uri.decode(raw);
        ArrayDeque<String> segments = new ArrayDeque<>();
        String[] parts = path.substring(1).split("/", -1);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.equals("..")) {
                if (!segments.isEmpty()) segments.removeLast();
            } else if (!part.equals(".")) {
                segments.addLast(part);
                continue;
            }
            if (index == parts.length - 1) segments.addLast("");
        }
        return "/" + String.join("/", segments);
    }

    final Address address;
    final long pausedUntil;

    Rule(Address address, long pausedUntil) {
        this.address = address;
        this.pausedUntil = pausedUntil;
    }

    boolean isPaused(long now) {
        return pausedUntil > now;
    }

    boolean matches(Address candidate, long now) {
        if (isPaused(now) || candidate == null) return false;
        if (address.port() != -1 && address.port() != candidate.port()) return false;
        boolean wholeSite = address.path().equals("/");
        if (!address.host().equals(candidate.host())) {
            if (!wholeSite || !candidate.host().endsWith("." + address.host())) return false;
        }
        return wholeSite || candidate.path().equals(address.path())
                || candidate.path().startsWith(address.path() + "/");
    }
}
