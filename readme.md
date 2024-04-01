# A Simple Netty Based On JAVA NIO

> 基于Java NIO 写的一个简单版 Netty 服务端

## 前置知识

### NIO

- NIO 一般指 **同步非阻塞** IO，同样用于**描述程序访问数据方式 **的还有BIO（同步阻塞）、AIO（异步非阻塞）
- 同步异步指获取结果的方式，同步为主动去获取结果，不管结果是否准备好，异步为等待结果准备好的通知
- 阻塞非阻塞是线程在结果没有到来之前，是否进行等待，阻塞为进行等待，非阻塞则不进行等待
- NIO 主动地去获取结果，但是在结果没有准备好之前，不会进行等待。而是通过一个 **多路复用器** 管理多个通道，由一个线程轮训地去检查是否准备好即可。在网络编程中，多路复用器通常由操作系统提供，Linux中主要有 select、poll、epoll。同步非阻塞指线程不等待数据的传输，而是完成后由多路复用器通知，线程再将数据从内核缓冲区拷贝到用户空间内存进行处理。

### Java NIO

- 基于 NIO 实现的网络框架，可以用少量的线程，处理大量的连接，更适用于高并发场景。于是，Java提供了NIO包提供相关组件，用于实现同步非阻塞IO
    - 核心三个类Channel、Buffer、Selector。Channel代表一个数据传输通道，但不进行数据存取，有Buffer类进行数据管理，Selector为一个复用器，管理多个通道

### Bytebuffer

- 该类为NIO 包中用于操作内存的抽象类，具体实现由HeapByteBuffer、DirectByteBuffer两种
- HeapByteBuffer为堆内内存，底层通过 byte[ ] 存取数据
- DirectByteBuffer 为堆外内存，通过JDK提供的 Unsafe类去存取；同时创建对象会关联的一个Cleaner对象，当对象被GC时，通过cleaner对象去释放堆外内存

## 各核心组件介绍

### NioServer

> 为启动程序类，监听端口，初始化Channel

- 下面为NIO模式下简单服务端处理代码

```java
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
        // read事件 (内核缓冲区的数据准备好了)
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
```

### EventLoop

> 处理 Channel 中数据的读写

- 在上面的Server中，大量并发时单线程地处理读写事件会导致延迟，因此将读写处理抽取出来，可利用多线程实现高并发
- 一个EventLoop会关联一个selector，只会处理这个selector上的Channel

```java
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

```

### EventloopGroup

> 一组EventLoop，轮训地为eventLoop分配Channel

```java
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
```

### Channel

> 封装了SocketChannel 和 Pipline，将从Channel读写的消息，沿着Pipline上的节点进行处理

- 在上面EventLoop中，注册Channel到对应的Selector前，会进行封装，将自定义的Channel放在读写事件触发时会返回的SelectionKey里面
- 同时提供了数据读写处理方法，读写事件触发时调用该方法，数据会沿着pipline上去处理

```java
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

```

### Handler 和 HandlerContext

> handler 接口定义了可以扩展处理的消息，由开发人员实现具体的处理
>
> handlerContext 类封装了handler的实现类，将handler的上一个节点和下一个节点，让消息可以延者链表传递

```java
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
```

```java
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

```

### Pipline

> 本质是链表，包含了头尾节点的HandlerContext，提供方法给开发人员加节点

```java
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

    public static class PipHandler implements Handler{

        @Override
        public void channelRead(HandlerContext ctx, Object msg) {
            System.out.println("接收"+(String) msg +"进行资源释放");
        }

        @Override
        public void write(HandlerContext ctx, Object msg) {
            System.out.println("写出"+msg.toString());
        }

        @Override
        public void flush(HandlerContext ctx) {
            System.out.println("flush");
        }
    }
}
```

