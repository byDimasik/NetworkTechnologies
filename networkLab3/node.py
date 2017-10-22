import queue
import threading
import socket
import pickle
import time
import random
import signal

import messages

WORK = threading.Event()


def signal_handler(signal, frame):
    WORK.clear()


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
        WORK.set()

        signal.signal(signal.SIGINT, signal_handler)

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
        self.node_socket.settimeout(1)
        self.node_socket_cond = threading.Condition()

        self.user_thread = threading.Thread(target=self.get_msg_from_user, args=(), daemon=True)
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
            if self.node_addresses.count(address) == 0:
                self.node_addresses.append(address)
        finally:
            self.node_addresses_cond.release()

    def remove_address(self, address):
        try:
            self.node_addresses_cond.acquire()
            try:
                self.node_addresses.remove(address)
            except ValueError:
                pass
        finally:
            self.node_addresses_cond.release()

    def checker_routine(self):
        while WORK.is_set() or len(self.activities) or self.receiver_thread.is_alive():
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
                    if address == self.parent_address:
                        print('Parent node dead. I\'m root')
                        self.parent_address = None
                    print("Connection lost:", address)
                    try:
                        self.node_addresses.remove(address)
                    except ValueError:
                        pass
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
        print('checker off')

    def receiver_routine(self):
        while WORK.is_set() or len(self.activities) or self.sender_thread.is_alive():
            try:
                data, address = self.node_socket.recvfrom(128000)
            except socket.timeout:
                continue

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

                elif type(message) == messages.DeathMessage:
                    print('Connection cancelled:', address)
                    if address == self.parent_address:
                        print('I\'m root')
                        self.parent_address = None
                    self.remove_address(address)

                elif type(message) == messages.ChangeParentMessage:
                    self.remove_address(self.parent_address)
                    print('Change parent to', message.get_parent_address())
                    self.parent_address = message.get_parent_address()
                    self.add_address(self.parent_address)
                    self.send_connect()
        print('receiver off')

    def sender_routine(self):
        while WORK.is_set():
            try:
                message = self.messages_queue.get(timeout=1)
            except queue.Empty:
                continue

            if type(message) == messages.AckMessage:
                self.sync_sendto(message, message.get_destination())

            elif type(message) == messages.ConnectMessage:
                self.sync_send_with_add_activities(message, self.parent_address)

            elif type(message) == messages.UserMessage:
                try:
                    self.node_addresses_cond.acquire()
                    for node_address in self.node_addresses:
                        if message.get_sender() == node_address:
                            continue

                        self.sync_send_with_add_activities(message, node_address)

                        sender = message.get_sender()
                        message = messages.UserMessage(message.get_nickname(), message.get_text())
                        message.set_sender(sender)
                finally:
                    self.node_addresses_cond.release()

        try:
            self.node_addresses_cond.acquire()

            if not self.parent_address:
                if len(self.node_addresses):
                    self.parent_address = self.node_addresses[0]

            if self.parent_address:
                message = messages.DeathMessage()
                self.sync_send_with_add_activities(message, self.parent_address)

                for node_address in self.node_addresses:
                    if node_address == self.parent_address:
                        continue

                    message = messages.ChangeParentMessage(self.parent_address)
                    self.sync_send_with_add_activities(message, node_address)
        finally:
            self.node_addresses_cond.release()

        print('sender off')

    def send_connect(self):
        message = messages.ConnectMessage()
        self.messages_queue.put(message)

    def send_ack(self, message_uuid, address):
        message = messages.AckMessage(message_uuid, address)
        self.messages_queue.put(message)

    def get_msg_from_user(self):
        while WORK.is_set():
            message = input()

            time.sleep(1)
            message = messages.UserMessage(self.nickname, message)
            self.messages_queue.put(message)

    def sync_send_with_add_activities(self, message, address):
        self.sync_sendto(message, address)
        self.sync_add_activities(address)

        message.set_destination(address)

        self.sync_add_sent_message(message)

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
