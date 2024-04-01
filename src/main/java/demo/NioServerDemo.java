package demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Alan
 * @Description
 * @date 2024.03.31 13:37
 */
public class NioServerDemo {
    public static void main(String[] args) throws Exception {
        // 1、创建服务端Channel，绑定端口并配置非阻塞
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(6666));
        serverSocketChannel.configureBlocking(false);

        // 2、创建多路复用器selector，并将channel注册到多路复用器上
        // 不能直接调用channel的accept方法，因为属于非阻塞，直接调用没有新连接会直接返回
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 3、循环处理多路复用器的IO事件
        while(true){

            // 3.1、select属于阻塞的方法，这里阻塞等待1秒
            // 如果返回0，说明没有事件处理
            if (selector.select(1000) == 0){
                System.out.println("服务器等待了1秒，无IO事件");
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
                    socketChannel.register(selector,SelectionKey.OP_READ, ByteBuffer.allocate(1024));
                    System.out.println("客户端连接:"+socketChannel.getRemoteAddress());
                }
                // read事件，内核缓冲区的数据准备好了
                if(key.isReadable()){
                    SocketChannel channel = (SocketChannel)key.channel();
                    ByteBuffer byteBuffer = (ByteBuffer)key.attachment();
                    try {
                        // 将数据写进buffer
                        int readNum = channel.read(byteBuffer);
                        if (readNum == -1){
                            System.out.println("读取-1时,表示IO流已结束");
                            channel.close();
                            break;
                        }
                        // 打印buffer
                        byteBuffer.flip();
                        byte[] bytes = new byte[readNum];
                        byteBuffer.get(bytes, 0, readNum);
                        System.out.println("读取到数据:" + new String(bytes));
                    } catch (IOException e) {
                        System.out.println("读取发生异常，广播socket");
                        channel.close();
                    }

                }
                // write事件 (操作系统有内存写出了)
                if (key.isWritable()){
                    SocketChannel channel = (SocketChannel)key.channel();
                    // 读取read时暂存数据
                    byte[] bytes = (byte[])key.attachment();
                    if (bytes != null){
                        System.out.println("可写事件发生，写入数据: " + new String(bytes));
                        channel.write(ByteBuffer.wrap(bytes));
                    }
                    // 清空暂存数据，并切换成关注读事件
                    key.attach(null);
                    key.interestOps(SelectionKey.OP_READ);
                }
                iterator.remove();
            }
        }
    }
}
