package com.gg.core;

/**
 * @author Alan
 * @Description
 * @date 2023.07.15 19:02
 */
public class HandlerContext {

    private Handler handler;

    MyChannel channel;

    HandlerContext prev;

    HandlerContext next;

    public HandlerContext(Handler handler, MyChannel channel) {
        this.handler = handler;
        this.channel = channel;
    }

    /**
     * 读消息的传递，从头节点开始往后传
     * @param msg
     */
    public void fireChannelRead(Object msg){
        HandlerContext next = this.next;
        if (next != null){
            next.handler.channelRead(next,msg);
        }
    }

    /**
     * 从尾节点开始往前传
     * @param msg
     */
    public void write(Object msg){
        HandlerContext prev = this.prev;
        if (prev != null){
            prev.handler.write(prev,msg);
        }
    }

    /**
     * 从尾节点开始往前传
     */
    public void flush(){
        HandlerContext prev = this.prev;
        if (prev != null){
            prev.handler.flush(prev);
        }
    }
}
