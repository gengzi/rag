import cn.hutool.json.JSONUtil;

import java.util.HashMap;

public class Test {

    public static void main(String[] args) {

        String test = "本周完成了哪些任务、整体进度如何。\n" +
                "本周完成了XXX需求开发，已经提测。项目整体进度比预期延迟1d，预计下周三可以开始正式测试。\n" +
                "\n" +
                "此处为图片信息，它的概要内容如下\n" +
                ":\n" +
                "这张图片展示了 **Docker镜像构建与启动** 以及 **接口调用示例** 两部分技术内容，具体如下：  \n" +
                "\n" +
                "\n" +
                "### 一、构建并启动（Docker操作）  \n" +
                "1. **构建Docker镜像**：  \n" +
                "   命令 `docker build -t markitdown-mcp.` 用于构建名为 `markitdown-mcp` 的Docker镜像（`.` 表示基于当前目录 Dockerfile 构建）。  \n" +
                "\n" +
                "2. **启动Docker容器**：  \n" +
                "   命令 `docker run -d -p 8000:8000 --name markitdown-service markitdown-mcp` 用于启动容器：  \n" +
                "   - `-d`：后台运行容器；  \n" +
                "   - `-p 8000:8000`：将主机端口 `8000` 映射到容器内端口 `8000`；  \n" +
                "   - `--name markitdown-service`：为容器命名为 `markitdown-service`；  \n" +
                "   - `markitdown-mcp`：指定用之前构建的镜像启动容器。  \n" +
                "\n" +
                "\n" +
                "### 二、接口调用示例（RESTful API使用）  \n" +
                "接口地址为 `http://localhost:8000/mcp`（容器启动后，通过主机 `localhost:8000` 访问），支持两种文件转换场景：  \n" +
                "\n" +
                "1. **转换本地文件**：  \n" +
                "   通过 `file://` 协议访问本地文件，需先挂载本地文件到容器内路径（如 `-v 本地路径:/app/input`）。示例 `curl` 命令：  \n" +
                "   ```bash\n" +
                "   curl -X POST \"http://localhost:8000/mcp\" \n" +
                "     -H \"Content-Type: application/json\" \n" +
                "     -d '{\n" +
                "           \"tool\": \"convert_to_markdown\",\n" +
                "           \"parameters\": {\n" +
                "             \"uri\": \"file:///app/input/test.docx\"  # 容器内文件路径（需挂载本地文件）\n" +
                "           }\n" +
                "         }'\n" +
                "   ```  \n" +
                "\n" +
                "2. **转换网络文件（HTTP/HTTPS URI）**：  \n" +
                "   直接通过公开可访问的网络URL（如 `https://example.com/test.docx`）传递文件。示例 `curl` 命令：  \n" +
                "   ```bash\n" +
                "   curl -X POST \"http://localhost:8000/mcp\" \n" +
                "     -H \"Content-Type: application/json\" \n" +
                "     -d '{\n" +
                "           \"tool\": \"convert_to_markdown\",\n" +
                "           \"parameters\": {\n" +
                "             \"uri\": \"https://example.com/test.docx\"  # 网络可访问的Word文件\n" +
                "           }\n" +
                "         }'\n" +
                "   ```  \n" +
                "\n" +
                "\n" +
                "总结：图片核心是指导用户如何通过Docker部署服务，并通过RESTful API实现**本地文件**和**网络文件**到Markdown的转换。\n" +
                "\n" +
                "\n" +
                "### 2.相关数据\n" +
                "呈现相关数据以及背后的原因（如有）。";
        HashMap<String, String> jsonMap = new HashMap<>();
        jsonMap.put("d",test);
        System.out.println(JSONUtil.toJsonStr(jsonMap));
    }
}
