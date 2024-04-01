package com.gg.handler;

import com.gg.core.Handler;
import com.gg.core.HandlerContext;

import java.nio.ByteBuffer;

/**
 * @author Alan
 * @Description
 * @date 2023.07.16 11:20
 */
public class MyHandler1 implements Handler {
    @Override
    public void channelRead(HandlerContext ctx, Object msg) {
        System.out.println("handler1-进行解码处理");
        // 转成 byte[]
        ByteBuffer buffer = (ByteBuffer) msg;
        int limit = buffer.limit();
        byte[] content = new byte[limit];
        buffer.get(content);
        // byte[] 转 String
        String str = new String(content);
        // 传递给下一个
        ctx.fireChannelRead(str);
        // 释放资源
        buffer.clear();
    }

    @Override
    public void write(HandlerContext ctx, Object msg) {
        // string 转 byteBuffer
        System.out.println("hander1-编码"+msg.toString());
        ByteBuffer wrap = ByteBuffer.wrap(msg.toString().getBytes());
        // 传递给上一个
        ctx.write(wrap);
    }

    @Override
    public void flush(HandlerContext ctx) {
        System.out.println("hander1-flush");
        ctx.flush();
    }
}
