package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import java.math.BigInteger;

/**
 * Resolves exact integer segment widths for the Trinity storage capacity bar.
 */
record TrinityStorageCapacityLayout(int itemWidth,
                                    int fluidWidth,
                                    int otherWidth,
                                    int neutralWidth) {

    TrinityStorageCapacityLayout {
        if (itemWidth < 0 || fluidWidth < 0 || otherWidth < 0 || neutralWidth < 0) {
            throw new IllegalArgumentException("storage capacity segment widths must be non-negative");
        }
    }

    static TrinityStorageCapacityLayout calculate(int width,
                                                  BigInteger itemAmount,
                                                  BigInteger fluidAmount,
                                                  BigInteger otherAmount,
                                                  BigInteger capacity,
                                                  boolean unlimited) {
        validate(width, itemAmount, fluidAmount, otherAmount, capacity);
        if (width == 0) {
            return new TrinityStorageCapacityLayout(0, 0, 0, 0);
        }

        BigInteger totalAmount = itemAmount.add(fluidAmount).add(otherAmount);
        if (totalAmount.signum() == 0) {
            return unlimited ?
                    new TrinityStorageCapacityLayout(0, 0, 0, width) :
                    new TrinityStorageCapacityLayout(0, 0, 0, 0);
        }

        int filledWidth = filledWidth(width, totalAmount, capacity, unlimited);
        int[] segments = distribute(
                filledWidth,
                totalAmount,
                new BigInteger[] { itemAmount, fluidAmount, otherAmount });
        return new TrinityStorageCapacityLayout(segments[0], segments[1], segments[2], 0);
    }

    int filledWidth() {
        return itemWidth + fluidWidth + otherWidth + neutralWidth;
    }

    private static int filledWidth(int width,
                                   BigInteger totalAmount,
                                   BigInteger capacity,
                                   boolean unlimited) {
        if (unlimited || capacity.signum() == 0) {
            return width;
        }
        BigInteger scaledAmount = totalAmount.multiply(BigInteger.valueOf(width));
        return scaledAmount.divide(capacity).min(BigInteger.valueOf(width)).intValueExact();
    }

    private static int[] distribute(int filledWidth,
                                    BigInteger totalAmount,
                                    BigInteger[] amounts) {
        int[] widths = new int[amounts.length];
        BigInteger[] remainders = new BigInteger[amounts.length];
        BigInteger scaledWidth = BigInteger.valueOf(filledWidth);
        int allocatedWidth = 0;

        for (int index = 0; index < amounts.length; index++) {
            BigInteger[] quotientAndRemainder = amounts[index]
                    .multiply(scaledWidth)
                    .divideAndRemainder(totalAmount);
            widths[index] = quotientAndRemainder[0].intValueExact();
            remainders[index] = quotientAndRemainder[1];
            allocatedWidth += widths[index];
        }

        boolean[] receivedRemainderPixel = new boolean[amounts.length];
        for (int remaining = filledWidth - allocatedWidth; remaining > 0; remaining--) {
            int selected = selectLargestRemainder(remainders, receivedRemainderPixel);
            widths[selected]++;
            receivedRemainderPixel[selected] = true;
        }
        return widths;
    }

    private static int selectLargestRemainder(BigInteger[] remainders, boolean[] receivedRemainderPixel) {
        int selected = -1;
        for (int index = 0; index < remainders.length; index++) {
            if (receivedRemainderPixel[index]) {
                continue;
            }
            if (selected < 0 || remainders[index].compareTo(remainders[selected]) > 0) {
                selected = index;
            }
        }
        if (selected < 0) {
            throw new IllegalStateException("largest-remainder allocation exceeded its segment count");
        }
        return selected;
    }

    private static void validate(int width,
                                 BigInteger itemAmount,
                                 BigInteger fluidAmount,
                                 BigInteger otherAmount,
                                 BigInteger capacity) {
        if (itemAmount == null || fluidAmount == null || otherAmount == null || capacity == null) {
            throw new NullPointerException("storage capacity inputs must be present");
        }
        if (width < 0) {
            throw new IllegalArgumentException("storage capacity bar width must be non-negative");
        }
        if (itemAmount.signum() < 0 || fluidAmount.signum() < 0 ||
                otherAmount.signum() < 0 || capacity.signum() < 0) {
            throw new IllegalArgumentException("storage capacity inputs must be non-negative");
        }
    }
}
