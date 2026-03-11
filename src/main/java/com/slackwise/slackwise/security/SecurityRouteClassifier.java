package com.slackwise.slackwise.security;

public final class SecurityRouteClassifier {

    private SecurityRouteClassifier() {
    }

    public static String classify(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        if (isWebhookRoute(path)) {
            return "webhook";
        }

        if (isAuthRoute(path)) {
            return "auth";
        }

        return null;
    }

    private static boolean isWebhookRoute(String path) {
        return path.startsWith("/api/slack/events")
            || path.startsWith("/api/connectwise/events");
    }

    private static boolean isAuthRoute(String path) {
        return path.startsWith("/api/slack/oauth/install")
            || path.startsWith("/api/slack/oauth/callback");
    }
}
