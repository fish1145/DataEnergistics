package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** Connects server-thread capture to an existing planner future without blocking the server or adding a worker. */
public final class CapturedPlanningFuture<C, T> implements Future<T> {

    private final CompletableFuture<C> capture;
    private final CompletableFuture<Future<T>> planned = new CompletableFuture<>();

    public CapturedPlanningFuture(CompletableFuture<C> capture, Function<C, Future<T>> beginPlanning) {
        this.capture = capture;
        capture.whenComplete((value, failure) -> {
            synchronized (this) {
                if (planned.isCancelled()) {
                    return;
                }
                if (failure != null) {
                    planned.completeExceptionally(failure);
                    return;
                }
                try {
                    planned.complete(beginPlanning.apply(value));
                } catch (RuntimeException exception) {
                    planned.completeExceptionally(exception);
                }
            }
        });
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        Future<T> downstream = planned.getNow(null);
        boolean cancelled = downstream == null ? planned.cancel(mayInterruptIfRunning) : downstream.cancel(mayInterruptIfRunning);
        capture.cancel(mayInterruptIfRunning);
        return cancelled;
    }

    @Override
    public boolean isCancelled() {
        return planned.isCancelled() || capture.isCancelled() ||
                planned.isDone() && !planned.isCompletedExceptionally() && planned.getNow(null).isCancelled();
    }

    @Override
    public boolean isDone() {
        return planned.isCompletedExceptionally() || planned.isDone() && planned.getNow(null).isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return planned.get().get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long started = System.nanoTime();
        long budget = unit.toNanos(timeout);
        Future<T> downstream = planned.get(timeout, unit);
        return downstream.get(Math.max(0L, budget - (System.nanoTime() - started)), TimeUnit.NANOSECONDS);
    }
}
