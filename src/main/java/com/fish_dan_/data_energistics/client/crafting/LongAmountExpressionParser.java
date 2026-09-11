package com.fish_dan_.data_energistics.client.crafting;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.OptionalLong;

public final class LongAmountExpressionParser {

    private LongAmountExpressionParser() {}

    public static OptionalLong parse(String input) {
        try {
            BigDecimal value = new Parser(input).parse();
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) return OptionalLong.empty();
            BigInteger integer = value.toBigIntegerExact();
            if (integer.compareTo(BigInteger.ONE) < 0 || integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return OptionalLong.empty();
            return OptionalLong.of(integer.longValueExact());
        } catch (RuntimeException ex) {
            return OptionalLong.empty();
        }
    }

    private static final class Parser {

        private final String s;
        private int p;

        Parser(String input) {
            s = input.replace(" ", "").toLowerCase(Locale.ROOT);
        }

        BigDecimal parse() {
            BigDecimal v = expression();
            if (p != s.length()) throw new IllegalArgumentException();
            return v;
        }

        BigDecimal expression() {
            BigDecimal v = term();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '+') {
                    p++;
                    v = v.add(term());
                } else if (c == '-') {
                    p++;
                    v = v.subtract(term());
                } else break;
            }
            return v;
        }

        BigDecimal term() {
            BigDecimal v = power();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '*') {
                    p++;
                    v = v.multiply(power());
                } else if (c == '/') {
                    p++;
                    BigDecimal d = power();
                    if (d.signum() == 0) throw new ArithmeticException();
                    v = v.divide(d);
                } else if (c == '(' || Character.isDigit(c) || c == '.') {
                    v = v.multiply(power());
                } else break;
            }
            return v;
        }

        BigDecimal power() {
            BigDecimal v = primary();
            if (p < s.length() && s.charAt(p) == '^') {
                p++;
                int n = power().intValueExact();
                v = v.pow(n);
            }
            return v;
        }

        BigDecimal primary() {
            if (p < s.length() && s.charAt(p) == '(') {
                p++;
                BigDecimal v = expression();
                if (p >= s.length() || s.charAt(p++) != ')') throw new IllegalArgumentException();
                return v;
            }
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            if (st == p) throw new IllegalArgumentException();
            if (p < s.length() && s.charAt(p) == 'e' && p + 1 < s.length() && (Character.isDigit(s.charAt(p + 1)) || s.charAt(p + 1) == '+' || s.charAt(p + 1) == '-')) {
                p++;
                if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
                int es = p;
                while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                if (es == p) throw new IllegalArgumentException();
            }
            BigDecimal v = new BigDecimal(s.substring(st, p));
            if (p < s.length()) {
                char u = s.charAt(p);
                long m = switch (u) {
                    case 'k' -> 1_000L;
                    case 'm' -> 1_000_000L;
                    case 'g' -> 1_000_000_000L;
                    case 't' -> 1_000_000_000_000L;
                    case 'p' -> 1_000_000_000_000_000L;
                    case 'e' -> 1_000_000_000_000_000_000L;
                    default -> 0L;
                };
                if (m != 0) {
                    p++;
                    v = v.multiply(BigDecimal.valueOf(m));
                }
            }
            return v;
        }
    }
}
