package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.integration.ModFlags;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

public final class PinyinUtil {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PinyinUtil() {}

    public static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    public static String normalizeSearch(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || isCjk(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    public static boolean matchesSearch(String text, String filter) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        String normalized = normalizeSearch(text);
        String normalizedFilter = normalizeSearch(filter);
        if (normalizedFilter.isEmpty()) {
            return true;
        }

        if (normalized.contains(normalizedFilter) || isSubsequenceMatch(normalizedFilter, normalized)) {
            return true;
        }

        if (ModFlags.isJechLoaded()) {
            return JechMatcher.contains(text, filter);
        }

        return false;
    }

    private static boolean isSubsequenceMatch(String filter, String variant) {
        if (filter.isEmpty()) {
            return true;
        }
        if (variant.isEmpty()) {
            return false;
        }
        int filterIndex = 0;
        for (int i = 0; i < variant.length() && filterIndex < filter.length(); i++) {
            if (variant.charAt(i) == filter.charAt(filterIndex)) {
                filterIndex++;
            }
        }
        return filterIndex == filter.length();
    }

    private static final class JechMatcher {

        private static Object searcher;
        private static boolean searcherTried;
        private static Optional<MethodHandle> searchMethod = Optional.empty();

        static boolean contains(String text, String filter) {
            Object s = getSearcher();
            if (s != null && searchMethod.isPresent()) {
                try {
                    Object result = searchMethod.get().invoke(s, text, filter);
                    if (result instanceof Boolean b) {
                        return b;
                    }
                    if (result instanceof Number n) {
                        return n.intValue() > 0;
                    }
                } catch (Throwable e) {
                    LOGGER.warn("[DE][Pinyin] PinIn direct search failed", e);
                }
            }
            try {
                return me.towdium.jecharacters.utils.Match.contains(text, filter);
            } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
                return false;
            }
        }

        private static Object getSearcher() {
            if (searcher != null || searcherTried) {
                return searcher;
            }
            searcherTried = true;
            try {
                Class<?> searcherClass = Class.forName("me.towdium.pinin.Searcher");
                Object contain = ReflectionAccess.getField(ReflectionAccess.findStaticField(searcherClass, "CONTAIN"), null);
                int containMode = contain instanceof Number n ? n.intValue() : 0;
                searcher = ReflectionAccess.newInstance("me.towdium.pinin.Searcher", new Class<?>[] { int.class }, containMode);
                if (searcher == null) {
                    searcher = ReflectionAccess.newInstance("me.towdium.pinin.Searcher", new Class<?>[0]);
                }
                searchMethod = findSearchMethod(searcherClass);
                return searcher;
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.warn("[DE][Pinyin] Failed to init PinIn Searcher", e);
                return null;
            }
        }

        private static Optional<MethodHandle> findSearchMethod(Class<?> searcherClass) {
            Class<?>[] returnTypes = {
                    boolean.class,
                    Boolean.class,
                    int.class,
                    Integer.class,
                    Number.class,
                    Object.class
            };
            for (Class<?> returnType : returnTypes) {
                try {
                    return Optional.of(MethodHandles.publicLookup().findVirtual(
                            searcherClass,
                            "search",
                            MethodType.methodType(returnType, String.class, String.class)));
                } catch (NoSuchMethodException | IllegalAccessException | SecurityException ignored) {}
            }
            return Optional.empty();
        }
    }
}
