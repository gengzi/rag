## 运行方式
```angular2html
docker 命令

- 查看现在正在运行的容器
docker ps
- 进入正在运行的容器
# 格式：docker exec -it <容器ID或名称> /bin/bash
docker exec -it markitdown-api-1 /bin/bash
# 重新构建运行容器
docker-compose up --build -d 
- 停止运行容器
docker-compose stop
构建镜像 
docker build -t my-app    
启动容器并进入交互式终端
docker run -it --rm <镜像> /bin/bash    

```

### 一些问题
```shell
*  问题1：docker 运行后，python自定义的类，在某个模块下引用不到
- Python 的导入机制在相对路径下的表现导致的，直接用 from converter import ... 会让 Python 从 ** 系统路径（而非当前目录）** 中查找模块，所以会报 ModuleNotFoundError
在 main.py 中用相对导入，告诉 Python 从当前目录的同级模块导入：
# main.py 第10行修改为
from .converter import convert_to_markdown
* 问题2： CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8050"] 命令含义：
若代码在 ./app 目录，main.py 中定义了 app 实例   app = FastAPI() 才可以运行
 

```