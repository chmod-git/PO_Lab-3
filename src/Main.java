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
