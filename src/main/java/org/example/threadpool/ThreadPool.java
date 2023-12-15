package org.example.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadPool implements Executor {

    /**
     * TODO: LinkedTransferQueue에 대한 조사 필요
     */
    private final BlockingQueue<Runnable> queue = new LinkedTransferQueue<>();

    private Thread[] threads;
    private AtomicBoolean started = new AtomicBoolean();

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

            /**
             * Thread를 Constructor안에서 Thread를 시작하는 경우, JVM 스펙에 따라서 문제가 발생할 수 있다.
             * Thread가 Queue를 참조하고 있는데, Queue가 어떤 시점에 초기화 되는지가 애매하다.
             * 따라서 Lazy하게 해서, 첫번쨰 실행이 왔을때, 시작을 하는 방식으로 해보자
             */
            //threads[i].start();
        }
    }

    @Override
    public void execute(Runnable command) {
        /**
         * 만약 동시에 execute를 호출한다면, 같은 thread에서 두번 호출될 수 있다.
         * CAS로 Atomic연산해서 동시성을 제어하자
         */
        if(started.compareAndSet(false, true)){
            //started = true;
            for (Thread thread : threads) {
                thread.start();
            }
        }
        queue.add(command);
    }


}
