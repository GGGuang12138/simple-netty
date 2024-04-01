package com.gg;

import com.gg.core.EventLoopGroup;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 代码拆分
 * @author: Alan
 * @since: 2022/12/24 10:51
 */
public class NioServer {

    public static void main(String[] args) throws Exception{
        // 1、创建服务端Channel，绑定端口并配置非阻塞
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(6666));
        serverSocketChannel.configureBlocking(false);

        // 2、创建多路复用器selector，并将channel注册到多路复用器上
        // 不能直接调用channel的accept方法，因为属于非阻塞，直接调用没有新连接会直接返回
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        EventLoopGroup eventLoop = new EventLoopGroup();
        // 3、循环处理多路复用器的IO事件
        while(true){

            // 3.1、select属于阻塞的方法，这里阻塞等待1秒
            // 如果返回0，说明没有事件处理
            if (selector.select(1000) == 0){
                continue;
            }
            // 3.2、遍历事件进行处理
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while(iterator.hasNext()){
                SelectionKey key = iterator.next();
                // accept事件，说明有新的客户端连接
                if (key.isAcceptable()){
                    // 新建一个socketChannel,注册到selector,并关联buffer
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    // 注册到专门处理读写事件的selector上
                    System.out.println("客户端连接:"+socketChannel.getRemoteAddress());
                    eventLoop.register(socketChannel,SelectionKey.OP_READ);
                }

                iterator.remove();
            }
        }
    }
}
