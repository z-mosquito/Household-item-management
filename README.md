注意:这个项目完全由cursor开发

# 实时视频物体检测系统

这是一个基于Android客户端和Windows服务器的实时视频物体检测系统。Android设备通过摄像头采集视频流，发送到服务器进行物体检测，并实时显示检测结果。

## 功能特点

- 实时视频流传输
- 基于YOLOv8的物体检测
- 边缘检测效果叠加
- 可配置的服务器连接
- 实时日志显示
- 自动重连机制
- 帧率控制（15fps）

## 系统要求

### Android客户端
- Android 6.0 或更高版本
- 摄像头权限
- 网络权限

### Windows服务器
- Python 3.10
- Windows 10/11
- CPU支持（无需GPU）

## 快速开始

### 服务器端设置

1. 进入 `windows_server` 目录
2. 运行安装脚本安装依赖：
   ```bash
   install_dependencies.bat
   ```
3. 启动服务器：
   ```bash
   start_server.bat
   ```
   首次运行时会自动下载YOLOv8模型（约20MB）

### Android客户端设置

1. 使用Android Studio打开 `android_client` 目录
2. 修改服务器地址（如需要）：
   - 默认地址：`http://192.168.125.160:5000/video_feed`
   - 可在应用界面中修改
3. 构建并运行应用

## 使用说明

1. 启动服务器
2. 运行Android应用
3. 在应用中输入服务器地址（首次运行会使用默认地址）
4. 点击"连接"按钮
5. 允许相机权限
6. 开始实时检测

## 项目结构

### Android客户端
- 使用CameraX进行相机操作
- OkHttp处理网络请求
- 视图绑定实现UI交互
- YUV到JPEG的实时转换

### Windows服务器
- Flask提供Web服务
- OpenCV处理图像
- YOLOv8进行物体检测
- 多线程处理视频流

## 使用的开源项目

### Android端
- [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) - Android相机库
- [OkHttp](https://square.github.io/okhttp/) - 网络请求库
- [ViewBinding](https://developer.android.com/topic/libraries/view-binding) - 视图绑定

### 服务器端
- [Flask](https://flask.palletsprojects.com/) - Web框架
- [OpenCV](https://opencv.org/) - 计算机视觉库
- [YOLOv8](https://github.com/ultralytics/ultralytics) - 物体检测模型
- [NumPy](https://numpy.org/) - 科学计算库

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交问题和改进建议！如果你想贡献代码：

1. Fork 本仓库
2. 创建你的特性分支
3. 提交你的改动
4. 推送到你的分支
5. 创建一个 Pull Request

## 注意事项

- 确保Android设备和服务器在同一局域网内
- 服务器地址应使用局域网IP
- 首次运行服务器时需要联网下载模型
- 图像处理可能会占用较多CPU资源


## 致谢

感谢所有开源项目的贡献者，他们的工作使本项目成为可能。
