import uuid


MSG_TYPE_USER_MESSAGE = 1
MSG_TYPE_ACK = 2
MSG_TYPE_CHANGE_PARENT = 3
MSG_TYPE_DEATH_NODE = 4
MSG_TYPE_CONNECT = 5


class Message:
    def __init__(self):
        self.message_uuid = uuid.uuid4().bytes
        self.sender_address = None
        self.destination = None

    def update_uuid(self):
        self.message_uuid = uuid.uuid4().bytes

    def set_sender(self, address):
        self.sender_address = address

    def set_destination(self, destination):
        self.destination = destination

    def get_sender(self):
        return self.sender_address

    def get_uuid(self):
        return self.message_uuid

    def get_destination(self):
        return self.destination


class UserMessage(Message):
    def __init__(self, nickname, text):
        Message.__init__(self)

        if not nickname:
            raise ValueError('Необоходимо передать nickname')

        self.nickname = nickname
        self.text = text

    def get_nickname(self):
        return self.nickname

    def get_text(self):
        return self.text


class ChangeParentMessage(Message):
    def __init__(self, parent_address):
        Message.__init__(self)

        if not parent_address:
            raise ValueError('Необходимо передать адрес нового родителя (IP, port)')

        self.parent_address = parent_address


class AckMessage(Message):
    def __init__(self, message_uuid, destination):
        Message.__init__(self)
        self.destination = destination
        self.message_uuid = message_uuid


class DeathMessage(Message):
    def __init__(self):
        Message.__init__(self)


class ConnectMessage(Message):
    def __init__(self):
        Message.__init__(self)
