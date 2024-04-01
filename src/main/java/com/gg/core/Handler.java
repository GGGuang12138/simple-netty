package com.gg.core;

/**
 * @author Alan
 * @Description
 * @date 2023.07.15 19:01
 */
public interface Handler {

    /**
     * 读取数据处理
     * @param ctx
     * @param msg
     */
    void channelRead(HandlerContext ctx,Object msg);

    /**
     * 写出数据
     * @param ctx
     * @param msg
     */
    void write(HandlerContext ctx,Object msg);

    /**
     * 刷下数据
     * @param ctx
     */
    void flush(HandlerContext ctx);
}
