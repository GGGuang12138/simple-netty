package com.gg.core;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Alan
 * @Description
 * @date 2023.07.15 17:40
 */
public class EventLoopGroup {
    private EventLoop2[] children = new EventLoop2[1];

    private AtomicInteger idx = new AtomicInteger(0);

    public EventLoopGroup() throws IOException {
        for (int i = 0; i < children.length; i++){
            children[i] = new EventLoop2();
        }
    }

    public EventLoop2 next(){
        // 轮训每一个children
        return children[idx.getAndIncrement() & (children.length - 1)];
    }

    public void register(SocketChannel channel,int ops){
        next().register(channel,ops);
    }
}
