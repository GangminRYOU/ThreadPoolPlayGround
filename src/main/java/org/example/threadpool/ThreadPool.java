package org.example.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadPool implements Executor {

    /**
     * TODO: LinkedTransferQueue에 대한 조사 필요
     */

    /**
     * {@link #ThreadPool(int)}
     * 주기적으로 Interrupt를 보내는 것은 좋지 않은 구현이다.
     * Interrupt가 아닌 Signal을 보낼 방법이 필요하다.
     * => 모든 Thread가 최소 1개의 Job을 가지게 하면 된다.
     * {@link java.util.LinkedList} 에 나오는 Terminator와 유사
     */
    private static final Runnable SHUTDOWN_TASK = () -> {};
    private final BlockingQueue<Runnable> queue = new LinkedTransferQueue<>();

    private Thread[] threads;
    private AtomicBoolean started = new AtomicBoolean();
    /**
     * 정상 종료를 위한 flag
     * JVM이 CPU core에 캐싱하면 메모리 동기화가 안될 수 있다.
     * 즉, core마다 서로 다른 state를 가지고 있을 수 있다.
     */
    private AtomicBoolean shutdown = new AtomicBoolean();
    /**
     * shutdown이 여러 번 호출 되는 것에 대한 방어를 위해 Atomic Variable사용
     */

    /**
     * 이렇게 생성하게 되면, 할일이 적든 많든 간에 Thread가 지정한 만큼 생기게 된다.
     * 만약 1000개의 스레드를 생성하는데 2개의 할일만 있다면, 자원이 낭비된다.
     */

    /** (12/16 - 원시 ThreadPool을 개선하기)
     * 1. ThreadPool을 정지하는 기능이 없다.
     * 2. ThreadPool의 Thread를 필요한 만큼 만들지 않고 무조건 최대한으로 만든다.
     * @param numOfThreads
     * Thread Pool같은 경우에 동시성 이슈가 항상 발생할 수 있기 때문에, 주의해야 한다.
     */
    public ThreadPool(int numOfThreads){
        threads = new Thread[numOfThreads];
        for (int i = 0; i < numOfThreads; i++) {
            threads[i] = new Thread(() -> {
                //...
                //Take의 경우 Blocking 형태로 무한정 기다리게 된다.
                //Queue에 작업이 들어오면, Thread가 대기하다가 실행하는 구조
                //try {
                    //Task를 지속적으로 받아서 수행해야하니까 무한 루프를 돌아야 한다.
                    //shutdown이 아닐때만 job을 take할수 있게 수정한다.
                /**
                 * shutdown일 때만 shutdown을 하는게 아니라, Queue에 Job이 없을떄만 Shutdown을 한다.
                 * shutdown이고, queue에 Job이 없을때만 반복한다.
                 * @implNote 하지만 문제가 있다. -> Thread 1과 Thread 2가 job이 queue에 하나 있을때,
                 * 둘다 queue가 비지 않았다고 여긴다. -> queue.take()
                 * 첫번째 Thread는 Job을 끝내겠지만, Job을 받지 못한 두번째 Thread는 interrupt를
                 * 해주지 않으면 계속 기다리게 된다.
                 * -> 두가지 방법이 있다.
                 * 1. 반복적으로 interrupt를 해서 Thread를 깨우기
                 * 2. 정해진 시간 정도만 queue.take()로 기다리기
                 */
                // 사용자가 무작위 하게 Queue에 넣는 job에서 InterruptedException을 발생시켜서
                // Thread를 종료시키는 경우를 방어하기 위해 while문 안에서 try catch를 잡는다.
                //while(!shutdown || !queue.isEmpty())
                /**
                 * {@link #SHUTDOWN_TASK}와 같은 terminator를 사용하면
                 * 1. Queue가 비어있는 상태를 보장하고, ThreadPool을 종료할 수 있기 때문에
                 * (SHUTDOWN_TASK를 받으면 자동 종료가 되니까) Queue의 상태를 확인하지 않아도 된다.
                 * 2. shutdown인지 확인 할 필요가 없어진다.
                 * -> SHUTDOWN_TASK를 수행하면 뒤에 Job을 받기 전에 Thread가 종료되어 버리기 때문에
                 * => 이제 shutdown은 {@link #shutdown()} 이 되었는데 Queue에 Job을 넣는것을 방어하기 위함으로만 사용된다.
                 */
                for(;;) {
                    try{
                        final Runnable task = queue.take();
                        //Shutdown task로 시그널을 보내면 Interrupt Signal을 보낼 이유도 없어진다.
                        if(task == SHUTDOWN_TASK){
                            break;
                            /**
                             * SHUTDOWN_TASK는 Thread가 한번만 받아도 return을 하기 때문에
                             * 하나의 Thread가 여러번 받을 이유는 없다.
                             * Thread갯수 만큼만 queue에 넣어주게 되면, 모든 Thread가 Shutdown에 들어가게 된다.
                             */
                        }else {
                            task.run();
                        }
                    //Throwable이냐 Exception이냐? throw new Error로 사용자가 던져버리면 Exception으로는 잡지 못한다.
                    }catch (Throwable throwable){
                        /**Java 언어 spec상 던질수 없다고 하지만, 실제로는 Interrupted Exception을 던질 수 있다.
                         * Exception의 타입을 E 와 같은 타입 파라미터로 던지게 되면 타입에 상관없이 모두 던질 수 있다.
                         * {@link ThreadPool#doThrowUnsafely(Throwable)}
                         * */
                        if(!(throwable instanceof InterruptedException)){
                            System.err.println("Unexpected Exception: ");
                            throwable.printStackTrace();
                        }
                    }
                }
                System.err.println("Shutting thread  '" + Thread.currentThread().getName() + '\'');
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
        if(shutdown.get()){
            throw new RejectedExecutionException();
        }
        /**
         * shutdown이 아닌 것을 확인하고, queue에 command를 넣으려는데, 누가 shutdown()을 호출한다면?
         * 그렇게 되면 SHUTDOWN이 추가되고 나서 queue에 Job이 추가되어서 Job이 실행이 되지 않는 경우가 발생할 수 있다.
         * -> 순서를 바꿔 줘야한다.
         * @problemNote
         * 1. Thread A : execute(task)를 호출한다.
         * 2. Thread A : shutdown == false임을 확인한다.
         * 3. Thread B : shutdown() 을 호출한다.
         * 4. Thread B : shutdown.compareAndSet(false, true)를 실행한다.
         * 5. Thread B : queue에 SHUTDOWN_TASK를 모두 집어 넣는다.
         * 6. Thread A : queue에 task를 추가한다. (!) --> task는 실행이 되지 않는다.
         * @solutionList
         * 1. 가장 단순한 방법 -> (2)false임을 확인하고, (6)queue에 task에 추가하는 부분을 lock을 잡아 처리하기
         * B도 (3), (4), (5)를 같은 Lock을 잡아서 가져가면 된다. => 그렇게 하면 execute를 할때마다 lock이 잡힘
         * 2. 2가지 상태 만들기
         * - shuttingDown, shutdown 만들기
         * 1. Thread A : execute(task)
         * 2. Thread A : shuttingDown == false
         * 3. Thread B : shutdown()
         * 4. Thread B : shuttingDown.compareAndSet(false, true)
         * 5. Thread A : if(!shutdown) queue.add(task);
         * 6. Thread B : shutdown = true
         * 7. Thread B : queue.add(SHUTDOWN_TASK)
         * => 이렇게 되버리면 뚫린다.
         * 3. {@link java.util.concurrent.ThreadPoolExecutor#execute(Runnable)} 참고하기
         * 1. Thread A : execute(task)를 호출한다.
         * 2. Thread A : shutdown == false임을 확인한다.
         * 3. Thread B : shutdown() 을 호출한다.
         * 4. Thread B : shutdown.compareAndSet(false, true)를 실행한다.
         * 5. Thread B : queue에 SHUTDOWN_TASK를 모두 집어 넣는다.
         * 6. Thread A : queue에 task를 추가한다. (!) --> task는 실행이 되지 않는다.
         * 7. Thread A : shutdown == true이면 queue에서 task를 빼고, RejectedExecutionException을 던진다.
         *  => 이렇게 해야 잡힌다.
         * {@link java.util.concurrent.ThreadPoolExecutor#ctl}의 상태 관리
         * 32 bit에서 3빼서 29bit를 Count로 가지고 Integer는 2 ^ 32개의 경우의 수를 가지는게 맞지.
         * 근데 지금 모든 상수에 29만큼 Shifting하고 있잖아
         * 그럼 어떤 값을 넣든 2 ^ 3을 넘어서는 값은 2 ^ 29만큼의 shifting을 통해서 절사되고
         * 2 ^ 3이하의 값만 가질 수 밖에 없게 된다.
         * */
        queue.add(command);
        if(shutdown.get()){
            queue.remove(command);
            /**
             * 만약 command에 equals를 overriding한 Runnable클래스를 받았다면?
             * 내가 넣은 Task가 아니라, 다른 Task가 삭제될 수 도 있다. => JDK에서도 잡아주진 않는다.
             */
            throw new RejectedExecutionException();
        }
        /**
         * 여기서 문제가 발생할 수 있다.
         * {@link ThreadPoolTest} 에서 Thread.sleep()함수 호출을 위해 Interrupted Exception을
         * try catch로 잡는 경우, 각 thread의 기본 동작을 interrupt 시키는게 아니라, 해당 job의 interrupt를
         * 발생시키기 때문에, thread가 shutdown이 되지 않는다.
         */
    }

    /**
     * {@link }
     * 사용자 입장에서는 execute를 하면 shutdown을 이후에 실행하더라도,
     * 이미 execute한 Job은 모두 해결되고, shutdown이 되기를 바란다.
     * 1. shutdown이 시작하면, 더 이상 Task를 받으면 안된다. {@link #execute(Runnable)}
     *  - shutdown을 했는데 execute를 계속 받아주면, Queue가 Empty되지 않을 수 도 있다.
     * 2. queue의 Job이 아예 없을 때 shutdown을 해야한다.
     */
    public void shutdown(){
        //TODO: CAS로 started상태 확인하기
        /**
         * SHUTDOWN 여러번 호출 방지
         */
        if(shutdown.compareAndSet(false, true)){
            for (int i = 0; i < threads.length; i++) {
                queue.add(SHUTDOWN_TASK);
            }
        }
        //TODO: Wait until the queue is completely drained.
        /**
         * 단순히 무한 Loop를 돌면서 Queue가 비어 있는지 확인할 수 도 있지만,
         * 불필요한 자원을 소모 하게 된다.
         */
        /**
         * thread가 100개인데, 하나씩 interrupt를 걸어주는 동안 또 자원이 낭비된다.
         * 따라서, 아래처럼 한번씩 미리 interrupt를 시도시킨다.
         * 따라서, 첫번째 thread가 shutdown될때 까지 기다리는 동안 두번쨰 thread도 이미 shutdown을 시작하게 된다.
         * 결국, 동시적으로 thread가 종료를 시작하게 된다.
         */

        /*for (Thread thread : threads) {
            thread.interrupt();
        }*/
        /**
         * @implNote if문 안에 넣어버리게 되면, Thread1 shutdown()호출해서 Atomic 연산 성공,
         * Thread2 shutdown()을 한번 더 호출 했을때 Atomic 연산이 실패하면서,
         * Thread2는 thread.join()을 하지 않게 됨으로, Thread1의 shutdown으로 인해
         * Thread2가 실제로 종료되기 전에 {@link #shutdown()}메소드가 return되게 되어 버린다.
         * 하지만, 이것은 우리가 원하는 동작이 아니다. -> 우리는 thread가 모두 정상 종료되고나서, shutdown메소드가
         * return되기를 원함
         */
        for (Thread thread : threads) {
            //TODO: Thread를 정지하는 코드
            //alive인 동안만 interrupt를 시도하며 돌면서 스레드가 멈추기를 기다리면, 불필요하게 CPU를 소모하게 된다.
            //따라서 종료될때 알려주는 장치가 필요
            do {
                //for (; ; ) {
                    try {
                        thread.join();
                        //여기서 메소드 예외로 전파하지 않는 이유는 thread가 반 쯤 종료된 상태에서 예외를 전파시켜버리면
                        //Thread Pool의 상태가 엉망이 되어버리기 때문이다.
                    } catch (InterruptedException e) {
                        //Do not propagate to prevent incomplete shutdown.
                    }
                    /*if(!thread.isAlive()){
                        break;
                    }
                    thread.interrupt();*/
                //}
            } while (thread.isAlive());
        }
        //doThrowUnsafely(new InterruptedException());
    }

    private static <E extends Throwable> void doThrowUnsafely(Throwable cause) throws E{
        throw (E) cause;
    }
}
