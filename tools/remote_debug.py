#!/usr/bin/env python3
"""
远程调试工具
用于连接到Flowpilot设备并查看日志
"""
import argparse
import sys
import os

# 添加项目根目录到Python路径
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

def main():
    parser = argparse.ArgumentParser(description='Flowpilot 远程调试工具')
    parser.add_argument('host', help='设备IP地址')
    parser.add_argument('--port', type=int, default=5555, help='日志服务器端口')
    parser.add_argument('--mode', choices=['logs', 'shell'], default='logs', help='调试模式')
    
    args = parser.parse_args()
    
    if args.mode == 'logs':
        # 启动远程日志查看器
        from tools.remote_log_viewer import start_remote_log_viewer
        start_remote_log_viewer(args.host, args.port)
    elif args.mode == 'shell':
        # 这里可以实现远程shell功能
        print(f"连接到 {args.host} 的远程shell功能正在开发中...")
        print("目前仅支持日志查看模式")

if __name__ == '__main__':
    main()
