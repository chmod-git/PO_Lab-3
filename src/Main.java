import java.util.*;
import java.util.concurrent.*;

class Task {
    private final int id;
    private final int executionTime;

    public Task(int id) {
        this.id = id;
        Random rand = new Random();
        this.executionTime = rand.nextInt(6) + 5;
    }

    public void run() {
        System.out.println("Task " + id + " started. Executing for " + executionTime + " seconds.");
        try {
            Thread.sleep(executionTime * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Task " + id + " interrupted.");
            return;
        }
        System.out.println("Task " + id + " completed.");
    }

    public int getId() {
        return id;
    }
}

class WorkerThread extends Thread {
    private final Queue<Task> taskQueue;
    private boolean running = true;

    public WorkerThread(Queue<Task> taskQueue) {
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        while (running) {
            Task task = null;
            synchronized (taskQueue) {
                while (taskQueue.isEmpty() && running) {
                    try {
                        taskQueue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (running) {
                    task = taskQueue.poll();
                }
            }
            if (task != null) {
                task.run();
            }
        }
    }

    public synchronized void shutdown() {
        running = false;
        interrupt();
    }

    public synchronized void setRunning(boolean status) {
        running = status;
    }
}

class ThreadPool {
    private final WorkerThread[] threads;
    private final Queue<Task> taskQueue;
    private long maxQueueFullTime = 0;
    private long minQueueFullTime = Long.MAX_VALUE;
    private static long queueFullStartTime = 0;
    private boolean flag = false;
    private int rejectedTasks = 0;
    private final List<Long> waitTimes = new ArrayList<>();

    public ThreadPool(int nThreads) {
        this.threads = new WorkerThread[nThreads];
        this.taskQueue = new LinkedList<>();

        for (int i = 0; i < nThreads; i++) {
            threads[i] = new WorkerThread(taskQueue);
            threads[i].start();
        }
    }

    public synchronized void submitTask(Task task) {
        synchronized (taskQueue) {
            long submitStart = System.nanoTime();
            if (taskQueue.size() >= 20) {
                rejectedTasks++;
                if (!flag) {
                    queueFullStartTime = System.currentTimeMillis();
                    flag = true;
                }
                return;
            }

            if (flag && taskQueue.size() < 20) {
                flag = false;
                updateQueueFullTime();
            }

            taskQueue.offer(task);
            taskQueue.notify();

            long submitEnd = System.nanoTime();
            waitTimes.add(submitEnd - submitStart);
        }
    }

    public synchronized void shutdown() {
        for (WorkerThread thread : threads) {
            thread.shutdown();
        }
    }

    public synchronized void shutdownAndExecute() {
        for (WorkerThread thread : threads) {
            thread.setRunning(false);
        }
        taskQueue.clear();

        for (WorkerThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void updateQueueFullTime() {
        long endTime = System.currentTimeMillis();
        long duration = endTime - queueFullStartTime;
        if (duration > maxQueueFullTime) maxQueueFullTime = duration;
        if (duration < minQueueFullTime && duration > 0) minQueueFullTime = duration;
    }

    public int getRejectedTasks() {
        return rejectedTasks;
    }

    public long getMaxQueueFullTime() {
        return maxQueueFullTime;
    }

    public long getMinQueueFullTime() {
        return minQueueFullTime;
    }

    public long getAverageWaitTime() {
        if (waitTimes.isEmpty()) return 0;
        long total = waitTimes.stream().mapToLong(Long::longValue).sum();
        return total / waitTimes.size();
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int numThreads = 6;
        int testDuration = 15_000;
        int producerCount = 4;

        ThreadPool threadPool = new ThreadPool(numThreads);
        ExecutorService producers = Executors.newFixedThreadPool(producerCount);

        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;

        for (int i = 0; i < producerCount; i++) {
            producers.submit(() -> {
                while (System.currentTimeMillis() < endTime) {
                    Task task = new Task((int) (Math.random() * 1000));
                    threadPool.submitTask(task);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }

        producers.shutdown();
        producers.awaitTermination(testDuration, TimeUnit.MILLISECONDS);

        threadPool.shutdownAndExecute();

        System.out.println("\n=== TEST RESULTS ===");
        System.out.println("Total worker threads created: " + numThreads);
        System.out.println("Average thread wait time: " + threadPool.getAverageWaitTime() + " ns");
        System.out.println("Max queue full time: " + threadPool.getMaxQueueFullTime() + " ms");
        System.out.println("Min queue full time: " + threadPool.getMinQueueFullTime() + " ms");
        System.out.println("Total rejected tasks: " + threadPool.getRejectedTasks());
    }
}