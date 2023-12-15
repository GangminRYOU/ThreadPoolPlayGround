package org.example.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;

public class ThreadPool implements Executor {

    /**
     * TODO: LinkedTransferQueue에 대한 조사 필요
     */
    private final BlockingQueue<Runnable> queue = new LinkedTransferQueue<>();

    private Thread[] threads;

    public ThreadPool(int numOfThreads){
        threads = new Thread[numOfThreads];
        for (int i = 0; i < numOfThreads; i++) {
            threads[i] = new Thread(() -> {
                //...
                //Take의 경우 Blocking 형태로 무한정 기다리게 된다.
                //Queue에 작업이 들어오면, Thread가 대기하다가 실행하는 구조
                try {
                    //Task를 지속적으로 받아서 수행해야하니까 무한 루프를 돌아야 한다.
                    for(;;){
                        final Runnable task = queue.take();
                        task.run();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                //기다리는 작업이 실행중일때 interrupt가 발생할 수 있기 떄문에 예외
            });
            threads[i].start();
        }
    }

    @Override
    public void execute(Runnable command) {
        queue.add(command);
    }


}
