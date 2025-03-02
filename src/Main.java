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