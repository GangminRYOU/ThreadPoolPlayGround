package org.example.threadpool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolTest {

    //@Test
    @RepeatedTest(1000) //재현 확률을 높이기 위한 테스트
    @DisplayName("가장 원시적인 ThreadPool Test")
    void submittedTasksAreExecuted() throws InterruptedException {
        final Executor executor = new ThreadPool(2);
        final int numTasks = 100;
        //Thread가 모두 끝날때 까지 기다리기 위한 CountDownLatch
        final CountDownLatch countDownLatch = new CountDownLatch(numTasks);
        for (int i = 0; i < numTasks; i++) {
            int finalI = i;
            executor.execute(() -> {
                System.err.println(Thread.currentThread().getId() + " executes a task" + finalI);
                // 더 명확히 각 Thread가 어떻게 작업을 처리하는지 보기위한 Sleep()
                //너무 짧으면 하나의 Thread로도 모두 처리가 가능해져 버린다.
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                countDownLatch.countDown();
            });
        }
        //latch가 0이 될때까지 기다린다.
        countDownLatch.await();
    }

}