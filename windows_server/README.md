# Real-Time Video Analysis Server

这是一个基于Python的实时视频分析服务器，它能够接收来自Android客户端的视频流，并进行实时边缘检测处理。

## 功能特点

- 使用Flask框架提供HTTP API服务
- 支持实时视频流接收和处理
- 实现基于OpenCV的边缘检测
- 提供实时视频预览窗口
- 支持多线程处理以确保流畅性

## 系统要求

- Python 3.10
- Windows操作系统
- 网络连接（用于接收视频流）

## 依赖项

主要的Python依赖包括：
- Flask==3.0.0：Web服务器框架
- opencv-python==4.8.1.78：图像处理库
- numpy==1.24.3：数值计算库

详细的依赖列表请查看 `requirements.txt`。

## 快速开始

### 首次使用

1. 双击运行 `install_dependencies.bat` 安装所需的Python包
2. 双击运行 `start_server.bat` 启动服务器

### 后续使用

- 直接双击 `start_server.bat` 即可启动服务器

## 停止服务器

有两种方式可以停止服务器：
1. 在视频窗口中按 'q' 键
2. 在命令行窗口中按 Ctrl+C

## API接口

### POST /video_feed
接收视频帧数据的端点

请求体格式：
```json
{
    "image": "base64编码的图像数据"
}
```

响应格式：
```json
{
    "status": "success"
}
```

## 注意事项

1. 确保防火墙允许5000端口的访问
2. 服务器和Android客户端需要在同一个网络中
3. 按'q'键可以关闭视频预览窗口并退出程序

## 目录结构

```
windows_server/
├── README.md               # 项目说明文档
├── requirements.txt        # Python依赖列表
├── video_analyzer.py       # 主程序代码
├── install_dependencies.bat # 安装依赖脚本
└── start_server.bat        # 启动服务器脚本
```

## 配置说明

服务器默认监听所有网络接口（0.0.0.0）的5000端口。如需修改，请在`video_analyzer.py`中修改`app.run()`的参数。
