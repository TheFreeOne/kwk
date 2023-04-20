package org.freeone.k8s.web.knife.config.websocket;

import com.google.common.io.ByteStreams;
import io.kubernetes.client.Exec;
import io.kubernetes.client.extended.kubectl.Kubectl;
import io.kubernetes.client.extended.kubectl.exception.KubectlException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import org.freeone.k8s.web.knife.utils.K8sUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author zhengkai.blog.csdn.net
 */
@Component
@ServerEndpoint("/wsexec/{k8sId}/{namespace}/{name}")
//@Component
public class WebSocketServer {

//    @Autowired
//    private ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    /**
     * 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
     */
    private static int onlineCount = 0;

    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     */
    private static ConcurrentHashMap<String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();

    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    /**
     * 接收userId
     */
    private String userId = "";

    private Thread readThread;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("k8sId") String k8sId, @PathParam("namespace") String namespace, @PathParam("name") String name) throws IOException, KubectlException {
        this.session = session;

        if (webSocketMap.containsKey(this.userId)) {
            WebSocketServer webSocketServer = webSocketMap.get(this.userId);

            webSocketMap.remove(this.userId);
            webSocketMap.put(this.userId, this);
            //加入set中
        } else {
            webSocketMap.put(this.userId, this);
            //加入set中
            addOnlineCount();
            //在线数加1
        }

        ApiClient apiClient = K8sUtils.apiClient(Long.parseLong(k8sId));
//        String name = "mytomcat-deployment-6695dbdd78-899xj";

//        String[] command = new String[]{"ls","-lh"};
        String[] command = new String[]{"tail", "-200f", "/usr/local/tomcat/logs/localhost_access_log.2023-04-20.txt"};
        boolean stdin = false;
        boolean tty = false;
        V1Pod pod = Kubectl.get(V1Pod.class).name(name).namespace(namespace).apiClient(apiClient).execute();
        String containerName = pod.getSpec().getContainers().get(0).getName();

        Exec exec = new Exec(apiClient);


        PrintStream out = System.out;
        PrintStream err = System.err;
        try {
            Process proc = exec.exec(pod, command, containerName, stdin, tty);
            // 输出结果 PipedInputStream
            InputStream inputStream = proc.getInputStream();
            if (inputStream instanceof PipedInputStream) {
                PipedInputStream pipedInputStream = (PipedInputStream) inputStream;
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(pipedInputStream, StandardCharsets.UTF_8));
                this.readThread = new Thread(
                        () -> {
//                            try {
//                                int data = pipedInputStream.read();
//                                while (data != -1) {
//                                    data = pipedInputStream.read();
//                                    this.session.getBasicRemote().sendText(String.valueOf((char) data));
//                                }
//                                System.out.println();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
                            String line = null;
                            while (true) {
                                try {
                                    line = bufferedReader.readLine();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (line != null) {

                                    if (this.session.isOpen()) {
                                        // 异步传输数据到客户端
                                        try {
                                            this.session.getBasicRemote().sendText(line);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                }
                            }

                        });
                this.readThread.setDaemon(Boolean.TRUE);
                this.readThread.setName("socket-log");
                this.readThread.start();
            }

            {
//                // 输入，命令 WebSocketStreamHandler$WebSocketOutputStream
//                OutputStream outputStream = proc.getOutputStream();
//                // 报错PipedInputStream
//                InputStream errorStream = proc.getErrorStream();
//                OutputStream sendStream = this.session.getBasicRemote().getSendStream();
//                copyAsync(proc.getInputStream(), out);
//                copyAsync(proc.getErrorStream(), err);
            }
            if (stdin) {
                copyAsync(System.in, proc.getOutputStream());
            }
            proc.waitFor();
        } catch (InterruptedException | ApiException | IOException ex) {
            throw new KubectlException(ex);
        }

        log.info("用户连接:" + this.userId + ",当前在线人数为:" + getOnlineCount());
//        try {
//
//            sendMessage("JSON.toJSONString(webSocketMessage)");
//        } catch (IOException e) {
//            log.error("用户:" + this.userId + ",网络异常!!!!!!");
//        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        if (webSocketMap.containsKey(userId)) {
            webSocketMap.remove(userId);
            //从set中删除
            subOnlineCount();
        }
        log.info("用户退出:" + userId + ",当前在线人数为:" + getOnlineCount());
        if (this.readThread != null) {
            this.readThread.isInterrupted();
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("用户消息:" + userId + ",报文:" + message);
        //可以群发消息
        //消息保存到数据库、redis
        if ("message".equals(message)) {
            try {


                //解析发送的报文
//                JSONObject jsonObject = JSON.parseObject(message);
                //追加发送人(防止串改)
//                jsonObject.put("fromUserId",this.userId);
//                String toUserId=jsonObject.getString("toUserId");
                //传送给对应toUserId用户的websocket
//                if(StringUtils.isNotBlank(toUserId)&&webSocketMap.containsKey(toUserId)){
//                    webSocketMap.get(toUserId).sendMessage(jsonObject.toJSONString());
//                }else{
//                    log.error("请求的userId:"+toUserId+"不在该服务器上");
//                    //否则不在这个服务器上，发送到mysql或者redis
//                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误:" + this.userId + ",原因:" + error.getMessage());
        error.printStackTrace();
    }

    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }


    /**
     * 发送自定义消息
     */
    public static void sendInfo(String message, @PathParam("userId") String userId) throws IOException {
        log.info("发送消息到:" + userId + "，报文:" + message);
//        if(StringUtils.isNotBlank(userId)&&webSocketMap.containsKey(userId)){
//            webSocketMap.get(userId).sendMessage(message);
//        }else{
//            log.error("用户"+userId+",不在线！");
//        }
    }


    protected static Thread copyAsync(InputStream in, OutputStream out) {
        Thread t =
                new Thread(
                        new Runnable() {
                            public void run() {
                                try {
                                    ByteStreams.copy(in, out);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            }
                        });
        t.start();
        return t;
    }


    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        WebSocketServer.onlineCount--;
    }
}
