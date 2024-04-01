package com.gg.core;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Alan
 * @Description
 * @date 2023.07.15 10:09
 */
public class EventLoop2 implements Runnable{


    private final Thread thread;
    /**
     * 复用器，当前线程只处理这个复用器上的channel
     */
    public Selector selector;
    /**
     * 待处理的注册任务
     */
    private final Queue<Runnable> queue = new LinkedBlockingQueue<>();

    /**
     * 初始化复用器，线程启动
     * @throws IOException
     */
    public EventLoop2() throws IOException {
        this.selector = SelectorProvider.provider().openSelector();
        this.thread = new Thread(this);
        thread.start();
    }

    /**
     * 将通道注册给当前的线程处理
     * @param socketChannel
     * @param keyOps
     */
    public void register(SocketChannel socketChannel,int keyOps){
        // 将注册新的socketChannel到当前selector封装成一个任务
        queue.add(()->{
            try {
                MyChannel myChannel = new MyChannel(socketChannel, this);
                SelectionKey key = socketChannel.register(selector, keyOps);
                key.attach(myChannel);
            } catch (Exception e){
                e.printStackTrace();
            }
        });
        // 唤醒阻塞等待的selector线程
        selector.wakeup();
    }

    /**
     * 循环地处理 注册事件、读写事件
     */
    @Override
    public void run() {
        while (!thread.isInterrupted()){
            try {
                int select = selector.select(1000);
                // 处理注册到当前selector的事件
                if (select == 0){
                    Runnable task;
                    while ((task = queue.poll()) != null){
                        task.run();
                    }
                    continue;
                }
                // 处理读写事件
                System.out.println("服务器收到读写事件,select:" + select);
                processReadWrite();

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理读写事件
     * @throws Exception
     */
    private void processReadWrite() throws Exception{
        System.out.println(Thread.currentThread() + "开始监听读写事件");
        // 3.2、遍历事件进行处理
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while(iterator.hasNext()){
            SelectionKey key = iterator.next();
            MyChannel myChannel = (MyChannel)key.attachment();
            if(key.isReadable()){
                // 将数据读进buffer
                myChannel.doRead(key);
            }
            if (key.isWritable()){
                myChannel.doWrite(key);
            }
            iterator.remove();
        }
    }
}
