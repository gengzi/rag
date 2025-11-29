package com.gengzi.rag.agent.excalidaw;


import com.gengzi.rag.agent.deepresearch.util.ResourceUtil;
import com.gengzi.rag.util.CodeBlockCleaner;
import com.gengzi.request.ChatReq;
import com.gengzi.response.ChatMessageResponse;
import com.gengzi.response.ExcaildrawWebViewRes;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ExcalidrawAgent {


    @Autowired
    private ChatClient deepseekChatClientNoRag;

    @Autowired
    private ExcalidrawProperties excalidrawProperties;

    String prompt = "{\"type\":\"excalidraw\",\"version\":2,\"source\":\"https://excalidraw.com\",\"elements\":[{\"type\":\"rectangle\",\"version\":1,\"id\":\"login-page\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":150,\"y\":100,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#4dabf7\",\"width\":200,\"height\":60,\"text\":\"1. 访问登录页面\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"rectangle\",\"version\":1,\"id\":\"input-credentials\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":150,\"y\":200,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#4dabf7\",\"width\":200,\"height\":60,\"text\":\"2. 输入用户名密码\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"rectangle\",\"version\":1,\"id\":\"submit-form\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":150,\"y\":300,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#4dabf7\",\"width\":200,\"height\":60,\"text\":\"3. 提交登录表单\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"rectangle\",\"version\":1,\"id\":\"server-verify\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":150,\"y\":400,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#4dabf7\",\"width\":200,\"height\":60,\"text\":\"4. 服务器验证凭证\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"diamond\",\"version\":1,\"id\":\"decision\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":150,\"y\":500,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#ffd43b\",\"width\":200,\"height\":80,\"text\":\"5. 验证是否通过？\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"rectangle\",\"version\":1,\"id\":\"success\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":50,\"y\":600,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#40c057\",\"width\":150,\"height\":60,\"text\":\"6. 登录成功\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"rectangle\",\"version\":1,\"id\":\"fail\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":300,\"y\":600,\"strokeColor\":\"#000000\",\"backgroundColor\":\"#fa5252\",\"width\":150,\"height\":60,\"text\":\"6. 登录失败\",\"fontSize\":16,\"fontFamily\":1,\"textAlign\":\"center\",\"verticalAlign\":\"middle\"},{\"type\":\"arrow\",\"version\":1,\"id\":\"arrow1\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":250,\"y\":160,\"width\":0,\"height\":40,\"strokeColor\":\"#000000\",\"backgroundColor\":\"transparent\",\"points\":[[0,0],[0,40]]},{\"type\":\"arrow\",\"version\":1,\"id\":\"arrow2\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":250,\"y\":260,\"width\":0,\"height\":40,\"strokeColor\":\"#000000\",\"backgroundColor\":\"transparent\",\"points\":[[0,0],[0,40]]},{\"type\":\"arrow\",\"version\":1,\"id\":\"arrow3\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":250,\"y\":360,\"width\":0,\"height\":40,\"strokeColor\":\"#000000\",\"backgroundColor\":\"transparent\",\"points\":[[0,0],[0,40]]},{\"type\":\"arrow\",\"version\":1,\"id\":\"arrow4\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":250,\"y\":460,\"width\":0,\"height\":40,\"strokeColor\":\"#000000\",\"backgroundColor\":\"transparent\",\"points\":[[0,0],[0,40]]},{\"type\":\"arrow\",\"version\":1,\"id\":\"arrow5\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":150,\"y\":540,\"width\":100,\"height\":60,\"strokeColor\":\"#000000\",\"backgroundColor\":\"transparent\",\"points\":[[0,0],[-100,60]]},{\"type\":\"arrow\",\"version\":1,\"id\":\"arrow6\",\"fillStyle\":\"solid\",\"strokeWidth\":2,\"strokeStyle\":\"solid\",\"roughness\":1,\"opacity\":100,\"angle\":0,\"x\":350,\"y\":540,\"width\":100,\"height\":60,\"strokeColor\":\"#000000\",\"backgroundColor\":\"transparent\",\"points\":[[0,0],[100,60]]}],\"appState\":{\"viewBackgroundColor\":\"#ffffff\",\"gridSize\":20}}";


    public Flux<ServerSentEvent<ChatMessageResponse>> excalidrawGenerate(ChatReq req) {
        String content = deepseekChatClientNoRag.prompt()
                .system(ResourceUtil.loadFileContent(excalidrawProperties.getSysPrompt()))
                .user(req.getQuery())
                .call().content();
        return Flux.just(content).map(info -> {
            ExcaildrawWebViewRes excaildrawWebViewRes = new ExcaildrawWebViewRes();
            excaildrawWebViewRes.setContent(CodeBlockCleaner.cleanJsonBlock(info));
            excaildrawWebViewRes.setNodeName("excalidraw");
            return ServerSentEvent.<ChatMessageResponse>builder().data(ChatMessageResponse.ofExcalidraw(excaildrawWebViewRes)).build();
        });


    }


}
