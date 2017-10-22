import uuid


MSG_TYPE_USER_MESSAGE = 1
MSG_TYPE_ACK = 2
MSG_TYPE_CHANGE_PARENT = 3
MSG_TYPE_DEATH_NODE = 4
MSG_TYPE_CONNECT = 5


class Message:
    port = None
    sender_address = None
    message_uuid = None

    def __init__(self, port):
        self.message_uuid = uuid.uuid4().bytes
        self.port = port

    def set_sender_address(self, ip):
        self.sender_address = (ip, self.port)


class UserMessage(Message):
    nickname = None
    text = None

    def __init__(self, port, nickname, text):
        Message.__init__(self, port)

        if not nickname:
            raise ValueError('Необоходимо передать nickname')

        self.nickname = nickname
        self.text = text

    def get_nickname(self):
        return self.nickname

    def get_text(self):
        return self.text


class ChangeParentMessage(Message):
    parent_address = None

    def __init__(self, port, parent_address):
        Message.__init__(self, port)

        if not parent_address:
            raise ValueError('Необходимо передать адрес нового родителя (IP, port)')

        self.parent_address = parent_address


class AckMessage(Message):
    def __init__(self, port):
        Message.__init__(self, port)


class DeathMessage(Message):
    def __init__(self, port):
        Message.__init__(self, port)


class ConnectMessage(Message):
    def __init__(self, port):
        Message.__init__(self, port)
