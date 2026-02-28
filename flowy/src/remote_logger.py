import os
import time
import zmq
import cereal.messaging as messaging
from common.params import Params

# 设置环境变量
os.environ['ZMQ'] = '1'
os.environ['BASEDIR'] = './'

def start_remote_logger():
    """启动远程日志服务器"""
    print("Starting remote logger server...")
    
    # 初始化ZMQ上下文
    context = zmq.Context()
    
    # 创建发布套接字
    publisher = context.socket(zmq.PUB)
    publisher.bind("tcp://*:5555")  # 监听所有网络接口的5555端口
    
    # 订阅相关的消息
    sm = messaging.SubMaster([
        "controlsState", "carState", "carEvents", "modelV2", 
        "liveCalibration", "radarState", "deviceState"
    ])
    
    print("Remote logger server started. Listening on port 5555...")
    
    try:
        while True:
            # 更新消息
            sm.update(timeout=100)
            
            # 发布所有消息
            for name in sm.names:
                if sm.rcv_frame[name] > 0:
                    msg = sm[name]
                    if msg:
                        # 发布消息
                        publisher.send_multipart([
                            name.encode('utf-8'),
                            msg.to_bytes()
                        ])
                        
            time.sleep(0.01)  # 避免CPU占用过高
            
    except KeyboardInterrupt:
        print("Remote logger server stopped.")
    finally:
        publisher.close()
        context.term()

if __name__ == "__main__":
    start_remote_logger()
