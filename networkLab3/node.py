import queue
import threading
import socket
import pickle
import time
import random

import messages


class CyclicList:
    def __init__(self, capacity):
        self.capacity = capacity
        self.c_list = []
        self.position = 0

    def append(self, item):
        try:
            self.c_list.pop(self.position)
        except IndexError:
            pass

        self.c_list.insert(self.position, item)
        self.position = (self.position + 1) % self.capacity

    def contains(self, item):
        return self.c_list.count(item) > 0


class Node:
    def __init__(self, nickname, port, lost, parent_address=None):
        self.node_addresses = []
        self.node_addresses_cond = threading.Condition()
        self.messages_queue = queue.Queue()

        self.sent_ack = CyclicList(100)
        self.sent_messages = []
        self.sent_messages_cond = threading.Condition()

        self.activities = {}
        self.activities_cond = threading.Condition()

        self.nickname = nickname
        self.port = port
        self.lost = lost
        print(self.lost)
        if not (nickname or port or lost):
            raise ValueError('Nickname or port or lost == None')

        self.parent_address = None
        if parent_address:
            self.parent_address = (socket.gethostbyname(parent_address[0]), parent_address[1])
            self.add_address((socket.gethostbyname(parent_address[0]), parent_address[1]))
            self.send_connect()

        self.node_socket = socket.socket(type=socket.SOCK_DGRAM)
        self.node_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.node_socket.bind(('', self.port))
        self.node_socket_cond = threading.Condition()

        self.user_thread = threading.Thread(target=self.get_msg_from_user, args=())
        self.sender_thread = threading.Thread(target=self.sender_routine, args=())
        self.receiver_thread = threading.Thread(target=self.receiver_routine, args=())
        self.checker_thread = threading.Thread(target=self.checker_routine, args=())

        self.user_thread.start()
        self.sender_thread.start()
        self.receiver_thread.start()
        self.checker_thread.start()

    def add_address(self, address):
        try:
            self.node_addresses_cond.acquire()
            self.node_addresses.append(address)
        finally:
            self.node_addresses_cond.release()

    def checker_routine(self):
        while True:
            time.sleep(0.5)
            now_time = time.time()

            dead_nodes = []
            try:
                self.activities_cond.acquire()
                for address, last_activity in self.activities.items():
                    if (now_time - last_activity) > 5:
                        dead_nodes.append(address)

                for address in dead_nodes:
                    self.activities.pop(address)
            finally:
                self.activities_cond.release()

            try:
                self.node_addresses_cond.acquire()
                for address in dead_nodes:
                    print("Connection lost:", address)
                    self.node_addresses.remove(address)
            finally:
                self.node_addresses_cond.release()

            dead_index = []
            try:
                self.sent_messages_cond.acquire()
                for dead_address in dead_nodes:
                    for index, message in enumerate(self.sent_messages):
                        if message.get_destination() == dead_address:
                            dead_index.append(index)

                for i in dead_index:
                    self.sent_messages.pop(i)

                for message in self.sent_messages:
                    self.sync_sendto(message, message.get_destination())
            finally:
                self.sent_messages_cond.release()

    def receiver_routine(self):
        while True:
            data, address = self.node_socket.recvfrom(128000)

            if data:
                if random.randint(0, 99) < self.lost:
                    continue

                message = pickle.loads(data)

                if self.sent_ack.contains(message.get_uuid()):
                    self.send_ack(message.get_uuid(), address)
                    continue

                if type(message) == messages.AckMessage:
                    try:
                        self.activities_cond.acquire()
                        try:
                            self.activities.pop(address)
                        except KeyError:
                            pass
                    finally:
                        self.activities_cond.release()

                    remove_index = None
                    try:
                        self.sent_messages_cond.acquire()
                        for index, element in enumerate(self.sent_messages):
                            if element.get_uuid() == message.get_uuid():
                                remove_index = index
                                break

                        if remove_index is not None:
                            self.sent_messages.pop(remove_index)
                    finally:
                        self.sent_messages_cond.release()
                    continue

                self.send_ack(message.get_uuid(), address)
                self.sent_ack.append(message.get_uuid())

                if type(message) == messages.ConnectMessage:
                    print('Connect new node', address)
                    self.add_address(address)

                elif type(message) == messages.UserMessage:
                    print(message.get_nickname() + ':', message.get_text())
                    message = messages.UserMessage(message.get_nickname(), message.get_text())
                    message.set_sender(address)
                    self.messages_queue.put(message)

    def sender_routine(self):
        while True:
            message = self.messages_queue.get(block=True)

            if type(message) == messages.AckMessage:
                self.sync_sendto(message, message.get_destination())

            elif type(message) == messages.ConnectMessage:
                self.sync_sendto(message, self.parent_address)
                self.sync_add_activities(self.parent_address)

                message.set_destination(self.parent_address)

                self.sync_add_sent_message(message)

            elif type(message) == messages.UserMessage:
                try:
                    self.node_addresses_cond.acquire()
                    for node_address in self.node_addresses:
                        if message.get_sender() == node_address:
                            continue

                        self.sync_sendto(message, node_address)
                        self.sync_add_activities(node_address)

                        message.set_destination(node_address)

                        self.sync_add_sent_message(message)

                        sender = message.get_sender()
                        message = messages.UserMessage(message.get_nickname(), message.get_text())
                        message.set_sender(sender)
                finally:
                    self.node_addresses_cond.release()

    def send_connect(self):
        message = messages.ConnectMessage()
        self.messages_queue.put(message)

    def send_ack(self, message_uuid, address):
        message = messages.AckMessage(message_uuid, address)
        self.messages_queue.put(message)

    def get_msg_from_user(self):
        while True:
            message = input()

            message = messages.UserMessage(self.nickname, message)
            self.messages_queue.put(message)

    def sync_sendto(self, message, address):
        try:
            self.node_socket_cond.acquire()
            self.node_socket.sendto(pickle.dumps(message), address)
        finally:
            self.node_socket_cond.release()

    def sync_add_activities(self, address):
        try:
            self.activities_cond.acquire()
            self.activities[address] = time.time()
        finally:
            self.activities_cond.release()

    def sync_add_sent_message(self, message):
        try:
            self.sent_messages_cond.acquire()
            self.sent_messages.append(message)
        finally:
            self.sent_messages_cond.release()
