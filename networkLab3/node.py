import uuid
import queue
import threading
import struct
import socket
import pickle

MSG_TYPE_USER_MESSAGE = 1
MSG_TYPE_ACK = 2
MSG_TYPE_CHANGE_PARENT = 3
MSG_TYPE_DEATH_NODE = 4
MSG_TYPE_CONNECT = 5


def generate_uuid():
    return uuid.uuid4()


"""
Три потока: первый ждет ввода сообщений и пихает их в очередь
            второй отправляет из очереди и ждет подтверждения доставки
            третий принимает сообщения от других
"""


class Message:
    sender_address = None
    message_type = None
    message_text = None
    nickname = None
    parent_address = None
    port = None

    def __init__(self, message_type, address=None, text=None, nickname=None, parent_address=None, port=None):
        self.sender_address = address
        self.message_type = message_type
        self.port = port

        if message_type == MSG_TYPE_USER_MESSAGE:
            if not (text or nickname):
                raise ValueError('Для типа user_message нужно передать nickname и text')

            self.message_text = text.encode()
            self.nickname = nickname.encode()

        if message_type == MSG_TYPE_CHANGE_PARENT:
            if not parent_address:
                raise ValueError('Для типа change_parent нужно передать parent_address = (IP, port)')

            self.parent_address = parent_address

        if message_type == MSG_TYPE_CONNECT:
            if not port:
                raise ValueError('Для типа connect нужно передать порт')

    def create_minimal_header(self, message_type):
        return generate_uuid().bytes + struct.pack('b', message_type)

    def build_message(self):
        if self.message_type == MSG_TYPE_USER_MESSAGE:
            return self.create_minimal_header(MSG_TYPE_USER_MESSAGE) + struct.pack('i', len(self.nickname)) + self.nickname + self.message_text
        elif self.message_type == MSG_TYPE_ACK or self.message_type == MSG_TYPE_DEATH_NODE or self.message_type == MSG_TYPE_CONNECT:
            return self.create_minimal_header(self.message_type)
        elif self.message_type == MSG_TYPE_CHANGE_PARENT:
            return self.create_minimal_header(MSG_TYPE_CHANGE_PARENT) + struct.pack('i', self.parent_address[1]) + self.parent_address[0]
        else:
            raise ValueError('Неизвестный тип сообщения')

    def is_connect(self):
        return self.message_type == MSG_TYPE_CONNECT


# FIXME отправка самому себе. Порт отправки и прослушки не равны
class Node:
    nickname = None
    parent_address = None
    node_addresses = []
    node_addresses_cond = threading.Condition()
    messages_queue = queue.Queue()
    user_thread = None
    sender_thread = None
    receiver_thread = None
    port = None

    def __init__(self, nickname, port, parent_address=None):
        self.nickname = nickname
        self.port = port
        if not (nickname or port):
            raise ValueError('Nickname == None')

        self.parent_address = parent_address
        if parent_address:
            self.add_address(parent_address)
            self.send_connect()

        self.user_thread = threading.Thread(target=self.get_msg_from_user, args=())
        self.sender_thread = threading.Thread(target=self.sender_routine, args=())
        self.receiver_thread = threading.Thread(target=self.receiver_routine, args=())

        self.user_thread.start()
        self.sender_thread.start()
        self.receiver_thread.start()

    def add_address(self, address):
        print(address)
        try:
            self.node_addresses_cond.acquire()
            self.node_addresses.append(address)
        finally:
            self.node_addresses_cond.release()

    def receiver_routine(self):
        receiver_socket = socket.socket(type=socket.SOCK_DGRAM)
        receiver_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        receiver_socket.bind(('', self.port))

        while True:
            data, address = receiver_socket.recvfrom(128000)

            if data:
                message = pickle.loads(data)
                message.sender_address = (address[0], message.port)
                if message.is_connect():
                    self.add_address((address[0], message.port))

                if message.message_type == MSG_TYPE_USER_MESSAGE:
                    print(message.message_text.decode())
                    self.messages_queue.put(message)

    def sender_routine(self):
        send_socket = socket.socket(type=socket.SOCK_DGRAM)

        while True:
            message = self.messages_queue.get(block=True)

            try:
                self.node_addresses_cond.acquire()
                for node_address in self.node_addresses:
                    if message.sender_address == node_address:
                        continue

                    print('send to', node_address)
                    send_socket.sendto(pickle.dumps(message), node_address)
            finally:
                self.node_addresses_cond.release()

    def send_connect(self):
        message = Message(MSG_TYPE_CONNECT, port=self.port)
        self.messages_queue.put(message)

    def get_msg_from_user(self):
        while True:
            message = input()

            message = Message(MSG_TYPE_USER_MESSAGE, text=message, nickname=self.nickname, port=self.port)
            self.messages_queue.put(message)

            # print(message)
