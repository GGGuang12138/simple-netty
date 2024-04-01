package com.gg.core;

import com.gg.core.EventLoop2;
import com.gg.core.Handler;
import com.gg.core.HandlerContext;
import com.gg.core.MyChannel;

import java.nio.ByteBuffer;

/**
 * @author Alan
 * @Description
 * @date 2023.07.15 19:02
 */
public class PipLine {

    private MyChannel channel;

    private EventLoop2 eventLoop;

    public HandlerContext headContext;

    public HandlerContext tailContext;

    public PipLine(MyChannel channel, EventLoop2 eventLoop) {
        this.channel = channel;
        this.eventLoop = eventLoop;
        PipHandler headHandler = new PipHandler();
        this.headContext = new HandlerContext(headHandler,channel);
        PipHandler tailHandler = new PipHandler();
        this.tailContext = new HandlerContext(tailHandler,channel);
        // 构建链表
        this.headContext.next = this.tailContext;
        this.tailContext.prev = this.headContext;
    }

    public void addLast(Handler handler){
        HandlerContext curr = new HandlerContext(handler, channel);

        // 连接在倒数第二个后面
        HandlerContext lastButOne = this.tailContext.prev;
        lastButOne.next = curr;
        curr.prev = lastButOne;

        // 连接在最后一个前面
        curr.next = tailContext;
        tailContext.prev = curr;

    }

    public class PipHandler implements Handler{

        @Override
        public void channelRead(HandlerContext ctx, Object msg) {
            System.out.println("tail-接收"+msg);
        }

        @Override
        public void write(HandlerContext ctx, Object msg) {
            System.out.println("head-写出"+msg.toString());
            if (!(msg instanceof ByteBuffer)){
                throw new RuntimeException("类型异常");
            }
            ctx.channel.doWriteQueue((ByteBuffer)msg);
        }

        @Override
        public void flush(HandlerContext ctx) {
            System.out.println("head-执行写出");
            ctx.channel.doFlush();
        }
    }
}
