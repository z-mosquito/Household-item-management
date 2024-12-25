from flask import Flask, request, Response, jsonify
import threading
import cv2
import numpy as np
import base64
import json
import logging
from datetime import datetime
from ultralytics import YOLO

app = Flask(__name__)

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('server.log'),
        logging.StreamHandler()
    ]
)
logger = app.logger

# 记录连接的客户端
connected_clients = set()

class VideoAnalyzer:
    def __init__(self):
        self.frame = None
        self.frame_lock = threading.Lock()
        self.running = True
        # 加载YOLOv8模型
        try:
            self.model = YOLO('yolov8n.pt')
            logger.info("YOLOv8模型加载成功")
        except Exception as e:
            logger.error(f"YOLOv8模型加载失败: {str(e)}")
            self.model = None

    def process_frame(self, frame):
        """物品检测和边缘检测"""
        if frame is None:
            return None
        
        # 顺时针旋转90度
        frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
        
        # 转换为灰度图并进行边缘检测
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        edges = cv2.Canny(gray, 100, 200)
        edges_color = cv2.cvtColor(edges, cv2.COLOR_GRAY2BGR)
        
        # 在原图上显示检测结果
        result = cv2.addWeighted(frame, 0.8, edges_color, 0.2, 0)

        # 进行物品检测
        if self.model is not None:
            try:
                # 运行YOLOv8检测
                detections = self.model(frame)[0]
                
                # 在图像上绘制检测结果
                for detection in detections.boxes.data.tolist():
                    x1, y1, x2, y2, conf, cls = detection
                    if conf > 0.5:  # 置信度阈值
                        # 获取类别名称
                        class_name = detections.names[int(cls)]
                        # 绘制边界框
                        cv2.rectangle(result, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)
                        # 添加类别标签
                        cv2.putText(result, f"{class_name} {conf:.2f}", 
                                  (int(x1), int(y1) - 10), 
                                  cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
                        logger.info(f"检测到物品: {class_name} (置信度: {conf:.2f})")
            except Exception as e:
                logger.error(f"物品检测失败: {str(e)}")
        
        return result

    def display_loop(self):
        cv2.namedWindow('Video Analysis', cv2.WINDOW_NORMAL)
        while self.running:
            with self.frame_lock:
                if self.frame is not None:
                    processed_frame = self.process_frame(self.frame.copy())
                    if processed_frame is not None:
                        cv2.imshow('Video Analysis', processed_frame)
            
            if cv2.waitKey(1) & 0xFF == ord('q') or cv2.getWindowProperty('Video Analysis', cv2.WND_PROP_VISIBLE) < 1:
                self.running = False
                # 强制退出程序
                import os
                os._exit(0)
                break

analyzer = VideoAnalyzer()

@app.route('/')
def index():
    return "Video Analysis Server Running"

@app.route('/video_feed', methods=['POST'])
def video_feed():
    client_ip = request.remote_addr
    if client_ip not in connected_clients:
        connected_clients.add(client_ip)
        logger.info(f"新设备连接: {client_ip} | 连接时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | 当前连接设备数: {len(connected_clients)}")

    if request.is_json:
        data = request.get_json()
        if 'image' in data:
            # 解码图像
            img_data = base64.b64decode(data['image'])
            nparr = np.frombuffer(img_data, np.uint8)
            frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            
            if frame is not None:
                with analyzer.frame_lock:
                    analyzer.frame = frame
                logger.debug(f"成功接收并处理来自 {client_ip} 的图像帧")
                return jsonify({'status': 'success'})

    logger.warning(f"收到无效请求 来自: {client_ip}")
    return jsonify({'status': 'error', 'message': 'Invalid request'})

@app.before_request
def before_request():
    # 更新客户端活动状态
    client_ip = request.remote_addr
    if client_ip in connected_clients:
        logger.debug(f"客户端活动: {client_ip} | 路径: {request.path} | 方法: {request.method}")

@app.errorhandler(Exception)
def handle_error(error):
    # 客户端断开连接时
    client_ip = request.remote_addr
    if client_ip in connected_clients:
        connected_clients.remove(client_ip)
        logger.info(f"设备断开连接: {client_ip} | 断开时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | 当前连接设备数: {len(connected_clients)}")
    logger.error(f"发生错误: {str(error)} | 客户端: {client_ip}")
    return str(error), 500

def start_flask():
    logger.info("服务器启动 | 监听地址: 0.0.0.0:5000")
    app.run(host='0.0.0.0', port=5000, debug=False)

if __name__ == '__main__':
    # 启动显示线程
    display_thread = threading.Thread(target=analyzer.display_loop)
    display_thread.daemon = True  # 设置为守护线程，这样主程序退出时线程也会退出
    display_thread.start()
    
    try:
        logger.info("正在启动服务器...")
        start_flask()
    except Exception as e:
        logger.error(f"服务器启动失败: {str(e)}")
    finally:
        logger.info("服务器关闭")
        analyzer.running = False
        cv2.destroyAllWindows()
