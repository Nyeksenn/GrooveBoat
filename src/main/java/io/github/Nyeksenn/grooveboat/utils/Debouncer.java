package io.github.Nyeksenn.grooveboat.utils;

import java.util.Map;
import java.util.concurrent.*;


public class Debouncer {
    private static volatile Debouncer instance;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    // Private constructor to prevent instantiation
    private Debouncer() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Gets the singleton instance (thread-safe, double-checked locking)
     */
    public static Debouncer getInstance() {
        if (instance == null) {
            synchronized (Debouncer.class) {
                if (instance == null) {
                    instance = new Debouncer();
                }
            }
        }
        return instance;
    }

    /**
     * Debounces a task with a unique key
     *
     * @param key      Unique identifier for this debounced task
     * @param runnable The task to execute after the delay
     * @param delay    The delay duration
     * @param timeUnit The time unit for the delay
     */
    public synchronized void debounce(String key, Runnable runnable, long delay, TimeUnit timeUnit) {
        // Cancel the previous task with this key if it exists
        ScheduledFuture<?> existingTask = scheduledTasks.get(key);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // Schedule the new task
        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            runnable.run();
            scheduledTasks.remove(key);  // Clean up after execution
        }, delay, timeUnit);

        scheduledTasks.put(key, newTask);
    }

    /**
     * Cancels a specific debounced task
     *
     * @param key The key of the task to cancel
     */
    public synchronized void cancel(String key) {
        ScheduledFuture<?> task = scheduledTasks.get(key);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            scheduledTasks.remove(key);
        }
    }

    /**
     * Cancels all pending debounced tasks
     */
    public synchronized void cancelAll() {
        for (ScheduledFuture<?> task : scheduledTasks.values()) {
            if (!task.isDone()) {
                task.cancel(false);
            }
        }
        scheduledTasks.clear();
    }

    /**
     * Shuts down the scheduler gracefully
     * Call this when your application is closing
     */
    public void shutdown() {
        cancelAll();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Force immediate shutdown
     */
    public void shutdownNow() {
        cancelAll();
        scheduler.shutdownNow();
    }
}