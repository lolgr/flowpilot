'p4a example service using oscpy to communicate with main application.'
from random import sample, randint
from string import ascii_letters
from time import localtime, asctime, sleep
import threading

from oscpy.server import OSCThreadServer
from oscpy.client import OSCClient

CLIENT = OSCClient('localhost', 3002)

# from opendbc.car.car_helpers import interfaces
# interfaces['VOLKSWAGEN_PASSAT_MK8']

import os
os.environ['ZMQ'] = '1'
os.environ['BASEDIR'] = './'

import cereal.messaging as messaging
sm = messaging.SubMaster(["radarState"])

# 导入远程日志服务器
import remote_logger

def ping(*_):
    'answer to ping messages'
    CLIENT.send_message(
        b'/message',
        [
            str().encode('utf8'),
        ],
    )


def send_date():
    'send date to the application'
    CLIENT.send_message(
        b'/message',
        ["hola".encode('utf8'), ],
    )


def start_remote_logger_thread():
    '启动远程日志服务器线程'
    logger_thread = threading.Thread(target=remote_logger.start_remote_logger)
    logger_thread.daemon = True
    logger_thread.start()
    print("Remote logger thread started")


if __name__ == '__main__':
    # 启动远程日志服务器
    start_remote_logger_thread()
    
    SERVER = OSCThreadServer()
    SERVER.listen('localhost', port=3000, default=True)
    SERVER.bind(b'/ping', ping)
    while True:
        # message = ss.receive()
        sm.update(timeout=10000)
        message = sm['radarState']
        if message:
            print("got message", message)
        else:
            print("no message")
