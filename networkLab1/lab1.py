import socket
import struct
from time import sleep, time
import threading
import sys
import signal
from os import system

MCAST_PORT = 5007
MY_TTL = 5
CLIENTS = {}
ADDR_INFO = socket.getaddrinfo(sys.argv[1], None)[0]
CV = threading.Condition()
RUN_EVENT = threading.Event()


def signal_handler(signal, frame):
    RUN_EVENT.clear()
    CV.acquire()
    CV.notifyAll()
    CV.release()
    send_thread.join()
    recv_thread.join()
    count_thread.join()


def sender_routine():
    sock_send = socket.socket(ADDR_INFO[0], socket.SOCK_DGRAM)
    ttl_bin = struct.pack('@i', MY_TTL)
    if ADDR_INFO[0] == socket.AF_INET:  # IPV4
        sock_send.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, ttl_bin)
    else:  # IPV6
        sock_send.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_MULTICAST_HOPS, ttl_bin)

    data = "data"
    while RUN_EVENT.is_set():
        sock_send.sendto(data.encode(), (ADDR_INFO[4][0], MCAST_PORT))
        sleep(1)

    sock_send.sendto("end".encode(), (ADDR_INFO[4][0], MCAST_PORT))


def receiver_routine():
    sock_recv = socket.socket(ADDR_INFO[0], socket.SOCK_DGRAM)
    sock_recv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock_recv.bind((ADDR_INFO[4][0], MCAST_PORT))

    group_bin = socket.inet_pton(ADDR_INFO[0], ADDR_INFO[4][0])
    if ADDR_INFO[0] == socket.AF_INET:  # IPV4
        mreq = group_bin + struct.pack('=I', socket.INADDR_ANY)
        sock_recv.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
    else:  # IPV6
        mreq = group_bin + struct.pack('@I', 0)
        sock_recv.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_JOIN_GROUP, mreq)

    while RUN_EVENT.is_set():
        data, address = sock_recv.recvfrom(1024)
        try:
            CV.acquire()
            old_size = len(CLIENTS)
            CLIENTS[address] = time()
            if (len(CLIENTS) != old_size) or data == "end".encode():
                if data == "end".encode():
                    CLIENTS.pop(address)
                CV.notify()
        finally:
            CV.release()


def counter_routine():
    while RUN_EVENT.is_set():
        system('clear')

        now = time()
        old_clients = []

        try:
            CV.acquire()
            for address, last_time in CLIENTS.items():
                if (now - last_time) > 5:
                    old_clients.append(address)

            for address in old_clients:
                CLIENTS.pop(address)

            print('Подключено клиентов:', len(CLIENTS))
            for address in CLIENTS.keys():
                print("   ", address)
            CV.wait(5)
        finally:
            CV.release()


if __name__ == "__main__":
    RUN_EVENT.set()

    send_thread = threading.Thread(target=sender_routine, args=())
    recv_thread = threading.Thread(target=receiver_routine, args=())
    count_thread = threading.Thread(target=counter_routine, args=())

    send_thread.start()
    recv_thread.start()
    count_thread.start()

    signal.signal(signal.SIGINT, signal_handler)


