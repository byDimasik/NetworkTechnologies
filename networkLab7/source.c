#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <assert.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <unistd.h>
#include <errno.h>

#define MAX_BUFFER_SIZE (8192)
#define MAX_CONNECTIONS (510)

volatile int exit_flag = 0;

void makeLeftShift(int* arr, const int size) {
    assert(arr);
    int i;
    for (i = 0; i < size - 1; i++) {
        arr[i] = arr[i + 1];
    }
}

void closeConnection(int* clientSocketp, int* serverSocketp, const int size) {
    shutdown(*clientSocketp, SHUT_RDWR);
    shutdown(*serverSocketp, SHUT_RDWR);

    close(*clientSocketp);
    close(*serverSocketp);

    makeLeftShift(clientSocketp, size);
    makeLeftShift(serverSocketp, size);
}

int getMyListenSocket(char* listenPort) {
    struct addrinfo hints = {0};
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_flags = AI_PASSIVE;
    hints.ai_addr = NULL;

    struct addrinfo *myAddres;
    int rc = getaddrinfo(NULL, listenPort, &hints, &myAddres);
    if (0 != rc) {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rc));
        return -1;
    }

    int mySocket = -1;
    struct addrinfo *i;
    for (i = myAddres; i != NULL; i = i->ai_next) {
        mySocket = socket(i->ai_family, i->ai_socktype, i->ai_protocol);
        if (-1 == mySocket) {
            continue;
        }

        int opt = 1;
        if (setsockopt(mySocket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(int))) {
            perror("listen socket");
        }

        rc = bind(mySocket, i->ai_addr, i->ai_addrlen);
        if (0 == rc) {
            break;
        }
        if (-1 == rc) {
            perror("listen");
        }

        close(mySocket);
    }

    if (NULL == i) {
        fprintf(stderr, "Could not bind my socket\n");
        return -1;
    }

    freeaddrinfo(myAddres);

    return mySocket;
}

int countMaxSocket(int serverSocket, int* socketsArr1, int* socketsArr2, int arrSize) {
    int max = serverSocket;
    int i;
    for (i = 0; i < arrSize; i++) {
        if (max < socketsArr1[i]) {
            max = socketsArr1[i];
        }

        if (max < socketsArr2[i]) {
            max = socketsArr2[i];
        }
    }

    return max;
}

int transferData(int fromSocket, int toSocket, const int connectionId, char* buffer) {
    int rc = read(fromSocket, buffer, MAX_BUFFER_SIZE);
    if (-1 == rc) {
        perror("read");
        return -1;
    } else if (0 == rc) {
        fprintf(stderr, "Connection #%d closed.\n", connectionId);
        return -1;
    }

    printf("Read %d bytes from client #%d.\n", rc, connectionId);

    int rc2 = write(toSocket, buffer, rc);
    if (rc2 != rc) {
        perror("write");
        return -1;
    }

    printf("Writen %d bytes to server #%d.\n", rc2, connectionId);

    return 0;
}

int connectToHost(char* host, char* port) {
    int hostSocket = -1;

    struct addrinfo serverHints = {0};
    serverHints.ai_family = AF_INET;
    serverHints.ai_socktype = SOCK_STREAM;

    struct addrinfo *serverAddres;

    int rc = getaddrinfo(host, port, &serverHints, &serverAddres);
    if (0 != rc) {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rc));
        return -1;
    }

    struct addrinfo *i;
    for (i = serverAddres; i != NULL; i = i->ai_next) {
        hostSocket = socket(i->ai_family, i->ai_socktype, i->ai_protocol);
        if (-1 == hostSocket) {
            continue;
        }

        if (0 == connect(hostSocket, i->ai_addr, i->ai_addrlen)) {
            break;
        }

        close(hostSocket);
    }

    freeaddrinfo(serverAddres);

    return hostSocket;
}

void sighandler(int signum) {
    exit_flag = 1;
}

int main(int argc, char** argv) {
    if (4 != argc) {
        fprintf(stderr, "Usage: %s listen_port target_host target_port\n", argv[0]);
        return -1;
    }
    signal(SIGINT, sighandler);

    int numConnectedClients = 0;
    int i;

    int mySocket = getMyListenSocket(argv[1]);
    if (-1 == mySocket) {
        return -1;
    }

    if (0 != listen(mySocket, MAX_CONNECTIONS)) {
        perror("listen");
        return -1;
    }

    fd_set inputSockets;
    fd_set outputSockets;

    int* clientSockets = (int*) calloc(MAX_CONNECTIONS, sizeof(int));
    assert(clientSockets);
    int* serverSockets = (int*) calloc(MAX_CONNECTIONS, sizeof(int));
    assert(serverSockets);
    char* messageBuffer = (char*) calloc(MAX_BUFFER_SIZE, sizeof(char));
    assert(messageBuffer);

    while (!exit_flag) {
        FD_ZERO(&inputSockets);
        FD_ZERO(&outputSockets);

        FD_SET(mySocket, &inputSockets);

        for (i = 0; i < numConnectedClients; i++) {
            FD_SET(clientSockets[i], &inputSockets);
            FD_SET(clientSockets[i], &outputSockets);
            FD_SET(serverSockets[i], &inputSockets);
            FD_SET(serverSockets[i], &outputSockets);
        }

        int maxSocket = countMaxSocket(mySocket, clientSockets, serverSockets, numConnectedClients);

	struct timeval timeout = {.tv_sec=10, .tv_usec=0};
        int rc = select(maxSocket + 1, &inputSockets, &outputSockets, NULL, &timeout);
	if (-1 == rc || 0 == rc) {
	    continue;
	}

        for (i = 0; i < numConnectedClients; i++) {
            if (FD_ISSET(clientSockets[i], &inputSockets) && FD_ISSET(serverSockets[i], &outputSockets)) {
                if (-1 == transferData(clientSockets[i], serverSockets[i], i, messageBuffer)) {
                    closeConnection(&clientSockets[i], &serverSockets[i], numConnectedClients - i);
                    numConnectedClients--;
                    break;
                }
            }

            if (FD_ISSET(serverSockets[i], &inputSockets) && FD_ISSET(clientSockets[i], &outputSockets)) {
                if (-1 == transferData(serverSockets[i], clientSockets[i], i, messageBuffer)) {
                    closeConnection(&clientSockets[i], &serverSockets[i], numConnectedClients - i);
                    numConnectedClients--;
                    break;
                }
            }
        }

        if (FD_ISSET(mySocket, &inputSockets)) {
            clientSockets[numConnectedClients] = accept(mySocket, NULL, NULL);

            serverSockets[numConnectedClients] = connectToHost(argv[2], argv[3]);
            if (-1 == serverSockets[numConnectedClients]) {
                fprintf(stderr, "Could not connect to server\n");
                close(clientSockets[numConnectedClients]);
            } else {
                printf("Client #%d connected.\n", numConnectedClients);
                numConnectedClients++;
            }
        }
    }

    printf("Closing...\n");

    for (i = 0; i < numConnectedClients; i++) {
        shutdown(clientSockets[i], SHUT_RDWR);
        shutdown(serverSockets[i], SHUT_RDWR);

        close(clientSockets[i]);
	    close(serverSockets[i]);
    }

    shutdown(mySocket, SHUT_RDWR);
    close(mySocket);

    free(clientSockets);
    free(serverSockets);

    free(messageBuffer);
}

