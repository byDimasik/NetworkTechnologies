import argparse
import sys

import node

ROOT = True


# Парсер аргументов командной строки
def create_cmd_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument('-n', '--nickname', required=True)
    parser.add_argument('-l', '--lost', required=True, type=int)
    parser.add_argument('-p', '--port', required=True, type=int)
    parser.add_argument('-pa', '--parent_address', required=False)
    parser.add_argument('-pp', '--parent_port', required=False, type=int)

    return parser


if __name__ == "__main__":
    p = create_cmd_parser()
    arguments = p.parse_args(sys.argv[1:])

    nickname = arguments.nickname
    lost = arguments.lost
    port = arguments.port
    if lost < 0 or lost > 100:
        print("Значение процента потерь должно быть от 0 до 100!!!")
        exit(1)

    parent_address = None
    parent_port = None
    if arguments.parent_address or arguments.parent_port:
        parent_address = arguments.parent_address
        parent_port = arguments.parent_port
        ROOT = False
        if not (parent_address or parent_port):
            print("Если передавать адрес родителя, то нужно передавать и адрес, и порт,"
                  " либо вообще ничего не передавать!!!")
            exit(1)

    print(nickname, lost, port, parent_address, parent_port)

    if ROOT:
        node = node.Node(nickname, port, lost)
    else:
        node = node.Node(nickname, port, lost, (parent_address, parent_port))
