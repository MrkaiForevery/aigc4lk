#### @网址导航

```shell
##mcp-service-tools
查询网址: https://mcp.so/

##大模型评测
https://superclueai.com/homepage

## github 加速工具下载
https://steampp.net/

## github网址
https://github.com/MrkaiForevery?tab=repositories

## gitee
https://gitee.com/

## 阿里云百炼apikey
访问网址: https://bailian.console.aliyun.com/cn-beijing?tab=model#/api-key

## spring-ai官网
https://docs.spring.io/spring-ai/reference/api/embeddings.html

## spring-ai-alibaba 官网
http://java2ai.com/docs/quick-start

## skill搜索网址
https://www.skills.sh/

```

#### @wsl子系统安装calude code

```shell
## 管理员身份在powrshell中运行: 
wsl --install -d Ubuntu-22.04

## 看版本
wsl --version

## 查看已安装的 Linux 发行版
wsl --list --verbose

## 进入Linux子系统
wsl ##(初次执行需要设置Linux系统原始账号和密码)
user: mrkai19940210
pwd: mrkai@19940210

## Linux子系统安装依赖
## 环境工具安装
sudo apt update && sudo apt upgrade -y
sudo apt install git curl build-essential python3-pip -y
git config --global user.name "mrkai"
git config --global user.email "17679237105@163.com"

## node.js安装--安装 nvm（Node版本管理工具）
## 使用http1.1
git config --global http.version HTTP/1.1
## 恢复使用http2
git config --global --unset http.version

## 国外的安装比较慢
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash
## 国内的安装比较快
curl -fsSL https://gitee.com/edazh/nvm/raw/master/install.sh | bash
source ~/.bashrc

# 设置当前会话的环境变量（临时生效）
export NVM_NODEJS_ORG_MIRROR=https://npmmirror.com/mirrors/node/

# 如果想永久生效，把上面这行追加到 ~/.bashrc
echo 'export NVM_NODEJS_ORG_MIRROR=https://npmmirror.com/mirrors/node/' >> ~/.bashrc
source ~/.bashrc
rm -rf ~/.nvm/.cache

nvm install --lts

nvm node_mirror
nvm node_mirror https://npmmirror.com/mirrors/node/

nvm npm_mirror
nvm npm_mirror https://npmmirror.com/mirrors/npm/


## claude code 安装
npm install -g @anthropic-ai/claude-code
source ~/.bashrc
claude --version

## 配置apikey
export ANTHROPIC_AUTH_TOKEN="你的API密钥"

如果你的 Windows 项目在 D:\MyProject，在 WSL 中，它对应的路径就是 /mnt/d/MyProject。注意，盘符名是小写，路径分隔符也要从 \ 改为 /

```

#### @wsl子系统使用calude code 分析windows里面的java项目

```shell
## 如果你的 Windows 项目在 D:\MyProject，在 WSL 中，它对应的路径就是 /mnt/d/MyProject
cd /mnt/f/airoot/langchain4j-demo

## 阿里云百炼apikey
访问网址： https://bailian.console.aliyun.com/cn-beijing?tab=model#/api-key

## calude code 配置apikey
cd ~/.claude
vi ~/.claude/settings.json
{
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "xxxx",
    "ANTHROPIC_BASE_URL": "https://dashscope.aliyuncs.com/apps/anthropic",
    "ANTHROPIC_MODEL": "deepseek-v3.2"
  }
}
##跳过 Claude 官方登录
vim ~/.claude
{"hasCompletedOnboarding":true}

## 启动 Claude Code：在项目路径下，直接运行 claude 命令启动。
claude

## 切换Claude code 的工作模式
终端直接按：shift+tab

## idea新的springboot提示插件
安装插件：打开 Settings → Plugins → 在 Marketplace 中搜索 Spring Explyt 并安装。
```

#### @安装向量数据库-Pinecone

```shell
## 这里使用云端Pinecone
云端网址: https://app.pinecone.io/organizations/-Ot9lE-XD775Ribm92US/projects/0571c699-a7c5-47c0-a631-422a528760a3/assistant-quickstart

```

