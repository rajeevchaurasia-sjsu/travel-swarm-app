package org.sjsu.travelswarm.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MarkdownUtil {
    private static final Pattern MARKDOWN_V2_ESCAPE_PATTERN =
            Pattern.compile("([_*()\\[\\]~`>#+\\-=|{}.!])"); // Note: added \\ before - to make it literal in regex

    public static String escapeMarkdownV2(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = MARKDOWN_V2_ESCAPE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement("\\" + matcher.group(1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}