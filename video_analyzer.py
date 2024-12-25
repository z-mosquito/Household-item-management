from flask import Flask, request, Response
import threading
import cv2
import numpy as np
import base64
import json

app = Flask(__name__)

class VideoAnalyzer:
    def __init__(self):
        self.frame = None
        self.frame_lock = threading.Lock()
        self.running = True

    def process_frame(self, frame):
        """简单的边缘检测示例"""
        if frame is None:
            return None
        
        # 转换为灰度图
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        # 使用Canny边缘检测
        edges = cv2.Canny(gray, 100, 200)
        # 转回彩色以便显示
        edges_color = cv2.cvtColor(edges, cv2.COLOR_GRAY2BGR)
        
        # 在原图上显示检测结果
        result = cv2.addWeighted(frame, 0.8, edges_color, 0.2, 0)
        return result

    def display_loop(self):
        cv2.namedWindow('Video Analysis', cv2.WINDOW_NORMAL)
        while self.running:
            with self.frame_lock:
                if self.frame is not None:
                    processed_frame = self.process_frame(self.frame.copy())
                    if processed_frame is not None:
                        cv2.imshow('Video Analysis', processed_frame)
            
            if cv2.waitKey(1) & 0xFF == ord('q'):
                self.running = False
                break

analyzer = VideoAnalyzer()

@app.route('/video_feed', methods=['POST'])
def video_feed():
    if request.method == 'POST':
        try:
            # 获取传入的图像数据
            data = request.get_json()
            if data and 'image' in data:
                # 解码Base64图像数据
                img_data = base64.b64decode(data['image'])
                nparr = np.frombuffer(img_data, np.uint8)
                frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
                
                with analyzer.frame_lock:
                    analyzer.frame = frame
                
                return json.dumps({'status': 'success'})
        except Exception as e:
            return json.dumps({'status': 'error', 'message': str(e)})
    
    return json.dumps({'status': 'error', 'message': 'Invalid request'})

def start_flask():
    app.run(host='0.0.0.0', port=5000)

if __name__ == '__main__':
    # 启动显示线程
    display_thread = threading.Thread(target=analyzer.display_loop)
    display_thread.start()
    
    # 启动Flask服务器
    flask_thread = threading.Thread(target=start_flask)
    flask_thread.start()
    
    try:
        display_thread.join()
    finally:
        analyzer.running = False
        cv2.destroyAllWindows()
