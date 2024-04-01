package com.gg.handler;

import com.gg.core.Handler;
import com.gg.core.HandlerContext;

/**
 * @author Alan
 * @Description
 * @date 2023.07.16 11:23
 */
public class MyHandler2 implements Handler {
    @Override
    public void channelRead(HandlerContext ctx, Object msg) {
        // 读取时的业务处理
        String str = (String)msg;
        System.out.println("hander2-接收"+ str);
        // 响应回复语
        ctx.channel.write("hello clint");
        // 刷新回复
        if ("flush\n".equals(str)){
            ctx.channel.flush();
        }
    }

    @Override
    public void write(HandlerContext ctx, Object msg) {
        // 写出时的业务处理
        System.out.println("handler2-写出"+msg.toString());
        // 传递给上一个
        ctx.write(msg);
    }

    @Override
    public void flush(HandlerContext ctx) {
        // 执行写出时处理
        System.out.println("handler2-flush");
        // 传递给上一个
        ctx.flush();
    }
}
