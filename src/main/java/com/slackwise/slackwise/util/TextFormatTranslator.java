package com.slackwise.slackwise.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatTranslator {
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");

    private static final Pattern SLACK_LINK_LABELED = Pattern.compile("<(https?://[^|>]+)\\|([^>]+)>");
    private static final Pattern SLACK_LINK_BARE = Pattern.compile("<(https?://[^>]+)>");
    private static final Pattern SLACK_STRIKE = Pattern.compile("(?<!~)~([^~]+)~(?!~)");
    private static final Pattern SLACK_BOLD = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
    private static final Pattern SLACK_ITALIC = Pattern.compile("(?<!_)_([^_]+)_(?!_)");

    private static final Pattern MARKDOWN_LINK = Pattern.compile("(?<!\\!)\\[([^\\]]+)\\]\\((https?://[^)]+)\\)");
    private static final Pattern MARKDOWN_STRIKE = Pattern.compile("~~([^~]+)~~");
    private static final Pattern MARKDOWN_BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern MARKDOWN_ITALIC = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");

    private TextFormatTranslator() {
    }

    public static String slackToConnectwise(String input) {
        return translate(input, TextFormatTranslator::slackSegmentToConnectwise);
    }

    public static String connectwiseToSlack(String input) {
        return translate(input, TextFormatTranslator::connectwiseSegmentToSlack);
    }

    private static String translate(String input, Function<String, String> segmentTransformer) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(input);
        int last = 0;
        StringBuilder out = new StringBuilder(input.length());

        while (matcher.find()) {
            String before = input.substring(last, matcher.start());
            out.append(translateInlineCode(before, segmentTransformer));
            out.append(matcher.group());
            last = matcher.end();
        }

        String tail = input.substring(last);
        out.append(translateInlineCode(tail, segmentTransformer));
        return out.toString();
    }

    private static String translateInlineCode(String segment, Function<String, String> segmentTransformer) {
        if (segment.isEmpty()) {
            return segment;
        }

        StringBuilder out = new StringBuilder(segment.length());
        int pos = 0;
        boolean inCode = false;

        while (true) {
            int tick = segment.indexOf('`', pos);
            if (tick == -1) {
                String chunk = segment.substring(pos);
                out.append(inCode ? chunk : segmentTransformer.apply(chunk));
                break;
            }

            String chunk = segment.substring(pos, tick);
            out.append(inCode ? chunk : segmentTransformer.apply(chunk));
            out.append('`');
            inCode = !inCode;
            pos = tick + 1;
        }

        return out.toString();
    }

    private static String slackSegmentToConnectwise(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }

        String converted = segment;
        converted = SLACK_LINK_LABELED.matcher(converted).replaceAll("[$2]($1)");
        converted = SLACK_LINK_BARE.matcher(converted).replaceAll("$1");
        converted = SLACK_STRIKE.matcher(converted).replaceAll("~~$1~~");
        converted = SLACK_BOLD.matcher(converted).replaceAll("**$1**");
        converted = SLACK_ITALIC.matcher(converted).replaceAll("*$1*");
        return converted;
    }

    private static String connectwiseSegmentToSlack(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }

        String converted = segment;
        converted = MARKDOWN_LINK.matcher(converted).replaceAll("<$2|$1>");
        converted = MARKDOWN_STRIKE.matcher(converted).replaceAll("~$1~");
        converted = MARKDOWN_ITALIC.matcher(converted).replaceAll("_$1_");
        converted = MARKDOWN_BOLD.matcher(converted).replaceAll("*$1*");
        return converted;
    }
}
