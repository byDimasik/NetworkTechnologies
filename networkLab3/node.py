import queue
import threading
import socket
import pickle

import messages


class CyclicList:
    def __init__(self, capacity):
        self.capacity = capacity
        self.c_list = []
        self.position = 0

    def append(self, item):
        self.c_list.insert(self.position, item)
        self.position = (self.position + 1) % self.capacity

    def contains(self, item):
        return self.c_list.count(item) > 0


class Node:
    def __init__(self, nickname, port, parent_address=None):
        self.node_addresses = []
        self.node_addresses_cond = threading.Condition()
        self.messages_queue = queue.Queue()

        self.nickname = nickname
        self.port = port
        if not (nickname or port):
            raise ValueError('Nickname == None')

        self.parent_address = parent_address
        if parent_address:
            self.add_address((socket.gethostbyname(parent_address[0]), parent_address[1]))
            self.send_connect()

        self.user_thread = threading.Thread(target=self.get_msg_from_user, args=())
        self.sender_thread = threading.Thread(target=self.sender_routine, args=())
        self.receiver_thread = threading.Thread(target=self.receiver_routine, args=())

        self.user_thread.start()
        self.sender_thread.start()
        self.receiver_thread.start()

    def add_address(self, address):
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
                message.add_sender(address[0])

                if type(message) == messages.ConnectMessage:
                    print('Connect new node', address)
                    self.add_address((address[0], message.port))

                elif type(message) == messages.UserMessage:
                    print(message.get_nickname() + ':', message.get_text())
                    message.add_sender(address[0], self.port)
                    self.messages_queue.put(message)

    def sender_routine(self):
        send_socket = socket.socket(type=socket.SOCK_DGRAM)

        while True:
            message = self.messages_queue.get(block=True)

            try:
                self.node_addresses_cond.acquire()
                for node_address in self.node_addresses:
                    if message.senders.count(node_address) > 0:
                        continue

                    send_socket.sendto(pickle.dumps(message), node_address)
            finally:
                self.node_addresses_cond.release()

    def send_connect(self):
        message = messages.ConnectMessage(self.port)
        self.messages_queue.put(message)

    def get_msg_from_user(self):
        while True:
            message = input()

            message = messages.UserMessage(self.port, self.nickname, message)
            self.messages_queue.put(message)
