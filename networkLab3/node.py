import queue
import threading
import socket
import pickle
import time
import random
import signal

import messages

WORK = threading.Event()    # Эвент работы узла
MAX_MSG_SIZE = 1171         # Максимальный размер сообщения: UserMessage с ником 20 символов и текстом 1000 символов


def signal_handler(signal, frame):
    """
    Если словили SIGINT прекращаем работу
    """
    WORK.clear()


class CyclicList:
    """
    Циклический список размера capacity. При переполнении начинает перезаписываться с начала
    """

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
    """
    Узел дерева. Работает в 4 потока:
        user_thread - поток демон, ожидает ввода сообщения от пользователя и пихает введенные сообщения в очередь
                      отправки
        sender_thread - поток, отправляющий сообщения, ожидающие отправки в очереди
        receiver_thread - поток, принимающий и обратывающий поступающие сообщения
        checker_thread - поток, отслеживающий мертвые узлы и недоставленные сообщения
    Каждый поток, кроме user_thread работает, пока WORK.is_set(). После сигинта потоки прекращают свою работу
    """
    def __init__(self, nickname, port, lost, parent_address=None):
        WORK.set()

        signal.signal(signal.SIGINT, signal_handler)

        self.node_addresses = []                            # список с адресами узлов, с которыми общаемся
        self.node_addresses_cond = threading.Condition()    # условная переменная для синхронизованного доступа
        self.messages_queue = queue.Queue()                 # очередь сообщений

        self.sent_ack = CyclicList(100)                     # циклический список с отправленными AckMessage
        self.sent_messages = []                             # список с остальными отправленными сообщениями
        self.sent_messages_cond = threading.Condition()     # условная переменная для синхронизованного доступа

        self.activities = {}                                # словарь активности узлов вида: (адрес : время активности)
        self.activities_cond = threading.Condition()        # условная переменная для синхронизованного доступа

        self.nickname = nickname                            # никнейм
        self.port = port                                    # порт
        self.lost = lost                                    # искусственный процент потерь
        if not (nickname or port or lost):
            raise ValueError('Nickname or port or lost == None')

        self.parent_address = None                          # адрес родителя
        if parent_address:
            # если был передан адрес родителя, запоминаем его и отправляем запрос на подключение
            self.parent_address = (socket.gethostbyname(parent_address[0]), parent_address[1])
            self.add_address((socket.gethostbyname(parent_address[0]), parent_address[1]))
            self.send_connect()

        self.node_socket = socket.socket(type=socket.SOCK_DGRAM)
        self.node_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.node_socket.bind(('', self.port))
        self.node_socket.settimeout(1)                  # таймаут для recvfrom
        self.node_socket_cond = threading.Condition()   # условная переменная для синхронизованного доступа

        self.user_thread = threading.Thread(target=self.get_msg_from_user, args=(), daemon=True)
        self.sender_thread = threading.Thread(target=self.sender_routine, args=())
        self.receiver_thread = threading.Thread(target=self.receiver_routine, args=())
        self.checker_thread = threading.Thread(target=self.checker_routine, args=())

        self.user_thread.start()
        self.sender_thread.start()
        self.receiver_thread.start()
        self.checker_thread.start()

    def add_address(self, address):
        """
        Добавление адреса в список с узлами, с которыми общаемся
        :param address: адрес для добавления
        """
        try:
            self.node_addresses_cond.acquire()
            if self.node_addresses.count(address) == 0:
                # новый адрес добавляем только в том случае, если такого адреса еще нет
                self.node_addresses.append(address)
        finally:
            self.node_addresses_cond.release()

    def remove_address(self, address):
        """
        Удаление адреса из списка узлов, с которыми общаемся
        :param address: адрес для удаления
        """
        try:
            self.node_addresses_cond.acquire()
            try:
                self.node_addresses.remove(address)
            except ValueError:
                # если такого адреса не было в списке, игнорируем запрос на удаление
                pass
        finally:
            self.node_addresses_cond.release()

    def checker_routine(self):
        """
        Функция потока, отслежвающего мертвые узлы и недоставленные сообщения
        Этот поток после сигинта должен умереть последним
        """
        while WORK.is_set() or len(self.activities) or self.receiver_thread.is_alive():
            time.sleep(0.5)             # работаем каждые полсекунды
            now_time = time.time()

            dead_nodes = []             # сюда складываем адреса отвалившихся узлов
            try:
                self.activities_cond.acquire()
                for address, last_activity in self.activities.items():
                    if (now_time - last_activity) > 5:  # узел отвалился, если не отвечает 5 секунд
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

            dead_index = []  # тут удаляем сообщения, которые предназначались для мертвых узлов
            try:
                self.sent_messages_cond.acquire()
                for dead_address in dead_nodes:
                    for index, message in enumerate(self.sent_messages):
                        if message.get_destination() == dead_address:
                            dead_index.append(index)

                for i in dead_index:
                    self.sent_messages.pop(i)

                # все остальные сообщения отправляем еще раз
                for message in self.sent_messages:
                    self.sync_sendto(message, message.get_destination())
            finally:
                self.sent_messages_cond.release()
        print('checker off')

    def receiver_routine(self):
        """
        Функция потока, принимающего сообщения
        Этот поток умирает, когда получит ответы на все отправленные сообщения, либо пока не убедится,
        что эти ответы не придут, потому что адресаты померли
        """
        while WORK.is_set() or len(self.activities) or self.sender_thread.is_alive():
            try:
                data, address = self.node_socket.recvfrom(MAX_MSG_SIZE)
            except socket.timeout:
                continue
            except ConnectionResetError:
                continue

            if data:
                if random.randint(0, 99) < self.lost:
                    # искусственно теряем пакет. Я не дцп, так было написано в ТЗ
                    continue

                message = pickle.loads(data)    # десериализуем полученное сообщение

                if self.sent_ack.contains(message.get_uuid()):
                    # если для сообщения с таким uuid уже был отправлен ack, значит, этот ack не дошел.
                    # отправляем по новой
                    self.send_ack(message.get_uuid(), address)
                    continue

                if type(message) == messages.AckMessage:
                    # если приняли ack, удаляем адрес узла, приславшего этот ack из списка узлов, от которых ждем ack
                    try:
                        self.activities_cond.acquire()
                        try:
                            self.activities.pop(address)
                        except KeyError:
                            pass
                    finally:
                        self.activities_cond.release()

                    # также удаляем сообщение, на которое пришел ack, из списка отправленных,
                    # чтобы его не переотправлять
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

                # раз сообщение пришло впервые и это не ack, отправляем ack, типа успешно все получили
                # и добаляем новый ack в список отправленных ack(ов)
                self.send_ack(message.get_uuid(), address)
                self.sent_ack.append(message.get_uuid())

                if type(message) == messages.ConnectMessage:
                    # если пришел запрос на коннект, добавляем адрес узла в список узлов для общения
                    print('Connect new node', address)
                    self.add_address(address)

                elif type(message) == messages.UserMessage:
                    # если пришло пользовательское сообщение, печатаем его и отправляем всем остальным узлам,
                    # за исключением того, от которого это сообщение пришло
                    print(message.get_nickname() + ':', message.get_text())
                    message = messages.UserMessage(message.get_nickname(), message.get_text())
                    message.set_sender(address)
                    self.messages_queue.put(message)

                elif type(message) == messages.DeathMessage:
                    # если пришло сообщение о смерти, скорбя удаляем адрес узла из списка узлов для общения
                    print('Connection cancelled:', address)
                    if address == self.parent_address:
                        # если помер родитель, забываем, что он у нас вообще был
                        print('I\'m root')
                        self.parent_address = None
                    self.remove_address(address)

                elif type(message) == messages.ChangeParentMessage:
                    # если пришел сообщение о смене родителя, удаляем старого родителя из списка узлов для общения
                    # запоминаем адрес нового родителя и отправляем ему запрос на коннект
                    self.remove_address(self.parent_address)
                    print('Change parent to', message.get_parent_address())
                    self.parent_address = message.get_parent_address()
                    self.add_address(self.parent_address)
                    self.send_connect()

        # уведомление о прекращении работы
        print('receiver off')

    def sender_routine(self):
        """
        Функция потока отправителя
        Этот поток при сигинте отправляет всем узлам-друзьям ня-пока и первым выпиливается
        :return:
        """
        while WORK.is_set():
            try:
                message = self.messages_queue.get(timeout=1)    # тягаем сообщение из очереди
            except queue.Empty:
                continue

            if type(message) == messages.AckMessage:
                # если отправляем ack, отправляем ack
                self.sync_sendto(message, message.get_destination())

            elif type(message) == messages.ConnectMessage:
                # если отправляем запрос на коннект, ............ отправляем запрос на коннект
                self.sync_send_with_add_activities(message, self.parent_address)

            elif type(message) == messages.UserMessage:
                # если отправляем пользовательско сообщение, тут чуть интересней
                try:
                    self.node_addresses_cond.acquire()
                    for node_address in self.node_addresses:
                        # идем по списку узлов, с которыми общаемся, проходим мимо того, от которого сообщение пришло
                        if message.get_sender() == node_address:
                            continue

                        self.sync_send_with_add_activities(message, node_address)

                        # создаем новому узлу новое сообщение с таким же текстом и ником
                        sender = message.get_sender()
                        message = messages.UserMessage(message.get_nickname(), message.get_text())
                        message.set_sender(sender)
                finally:
                    self.node_addresses_cond.release()

        # сюда попадаем после того, как нас вырубили сигинтом
        try:
            self.node_addresses_cond.acquire()

            # если узел был корнем, выбираем новым корнем первого попавшегося из списка узлов для общения
            # если в этом списке вообще кто-то есть
            if not self.parent_address:
                if len(self.node_addresses):
                    self.parent_address = self.node_addresses[0]

            # если был родитель или мы выбрали нового, отправляем родителю инфу, что мы помираем,
            # а остальным адрес нового родителя
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
        """
        Функция потока взаимодействующего с пользователем.
        Этот поток умирает, когда умрут все остальные, потому что он демон
        """
        while WORK.is_set():
            try:
                message = input()
            except KeyboardInterrupt:
                WORK.clear()
                continue

            if message == "kill me please":
                # альтернативный вариант завершения работы узла
                WORK.clear()
                continue

            if len(message) > 1000:
                print('Длина сообщения не должна превышать 1000 символов')
                continue

            message = messages.UserMessage(self.nickname, message)
            self.messages_queue.put(message)

    def sync_send_with_add_activities(self, message, address):
        """
        Функция отправки сообщения с добавлением информации и том, что будем ждать ответа от того, кому отправили
        """
        self.sync_sendto(message, address)
        self.sync_add_activities(address)

        message.set_destination(address)

        self.sync_add_sent_message(message)

    def sync_sendto(self, message, address):
        """
        Проста отправка сообщения без ожидания ответа (по факту используется только для отправки ack(ов))
        """
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
