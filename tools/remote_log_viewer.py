import zmq
import cereal.messaging as messaging
import cereal.log as log
import json

def start_remote_log_viewer(host="localhost", port=5555):
    """启动远程日志查看器"""
    print(f"Connecting to remote logger at {host}:{port}...")
    
    # 初始化ZMQ上下文
    context = zmq.Context()
    
    # 创建订阅套接字
    subscriber = context.socket(zmq.SUB)
    subscriber.connect(f"tcp://{host}:{port}")
    
    # 订阅所有消息
    subscriber.setsockopt_string(zmq.SUBSCRIBE, "")
    
    print("Connected. Receiving logs...")
    print("Press Ctrl+C to exit.")
    print("=" * 80)
    
    try:
        while True:
            # 接收消息
            topic, message = subscriber.recv_multipart()
            
            # 解析消息
            topic_str = topic.decode('utf-8')
            
            # 根据消息类型解析
            if topic_str == "controlsState":
                msg = log.ControlsState.from_bytes(message)
                print(f"[controlsState] Enabled: {msg.enabled}, State: {msg.state}")
            elif topic_str == "carState":
                msg = log.CarState.from_bytes(message)
                print(f"[carState] Speed: {msg.vEgo:.2f} m/s, Cruise: {msg.cruiseState.enabled}")
            elif topic_str == "carEvents":
                msg = log.CarEvent.from_bytes(message)
                print(f"[carEvents] Events: {[e for e in msg}")
            elif topic_str == "modelV2":
                msg = log.ModelDataV2.from_bytes(message)
                print(f"[modelV2] Lanes: {len(msg.laneLines)}")
            elif topic_str == "radarState":
                msg = log.RadarState.from_bytes(message)
                print(f"[radarState] Leads: {len(msg.leads)}")
            elif topic_str == "deviceState":
                msg = log.DeviceState.from_bytes(message)
                print(f"[deviceState] Thermal: {msg.thermalStatus}")
            else:
                print(f"[Unknown] Topic: {topic_str}, Length: {len(message)}")
                
    except KeyboardInterrupt:
        print("\nRemote log viewer stopped.")
    finally:
        subscriber.close()
        context.term()

if __name__ == "__main__":
    import sys
    host = sys.argv[1] if len(sys.argv) > 1 else "localhost"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
    start_remote_log_viewer(host, port)
