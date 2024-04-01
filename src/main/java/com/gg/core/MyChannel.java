package com.gg.core;

import com.gg.handler.MyHandler1;
import com.gg.handler.MyHandler2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 对应一个客户端连接
 * @author Alan
 * @Description
 * @date 2023.07.15 22:41
 */
public class MyChannel {

    private SocketChannel channel;

    private EventLoop2 eventLoop;

    private Queue<ByteBuffer> writeQueue;

    private PipLine pipLine;

    /**
     * 一个channel关联一个eventLoop、一个pipLine、一个socketChannel、一个writeQueue
     * @param channel
     * @param eventLoop
     */
    public MyChannel(SocketChannel channel, EventLoop2 eventLoop) {
        this.channel = channel;
        this.eventLoop = eventLoop;
        this.writeQueue = new ArrayDeque<>();
        this.pipLine = new PipLine(this,eventLoop);
        this.pipLine.addLast(new MyHandler1());
        this.pipLine.addLast(new MyHandler2());
    }

    /**
     * 读事件处理
     * @param key
     * @throws Exception
     */
    public void doRead(SelectionKey key) throws Exception{
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int readNum = channel.read(buffer);
            if (readNum == -1){
                System.out.println("读取-1时,表示IO流已结束");
                channel.close();
                return;
            }
            // 转成可读状态
            buffer.flip();
            // 消息放入pipLine，交给头节点, 头节点开始传递
            pipLine.headContext.fireChannelRead(buffer);

        } catch (IOException e) {
            System.out.println("读取发生异常，广播socket");
            channel.close();
        }
    }

    /**
     * 真正地写出数据，关注写事件后，会触发
     * @param key
     * @throws IOException
     */
    public void doWrite(SelectionKey key) throws IOException{
        ByteBuffer buffer;
        while ((buffer =writeQueue.poll()) != null){
            channel.write(buffer);
        }
        // 回复读取状态
        key.interestOps(SelectionKey.OP_READ);

    }

    /**
     * 写出到队列
     * @param msg
     */
    public void doWriteQueue(ByteBuffer msg){
        writeQueue.add(msg);
    }

    /**
     * 从最后一个节点进行写出，写出到头节点是调用doWriteQueue
     * @param msg
     */
    public void write(Object msg){
        this.pipLine.tailContext.write(msg);
    }

    /**
     * 从最后一个节点进行flush，写出到头节点时调用doFlush
     */
    public void flush(){
        this.pipLine.tailContext.flush();
    }

    /**
     * 关注写事件，才能进行真正地写出
     */
    public void doFlush(){
        this.channel.keyFor(eventLoop.selector).interestOps(SelectionKey.OP_WRITE);
    }

}
