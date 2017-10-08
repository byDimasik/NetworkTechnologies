package ru.nsu.fit.g15205.shishlyannikov;

import java.io.FileOutputStream;
import java.nio.file.Path;

public class ClientConnection {
    private FileOutputStream file;
    private long received = 0; // сколько получено за последние три секунды
    private long fileSize = 0; // размер файла, который должен получиться
    private long recorded = 0; // сколько всего записано
    private Path pathToFile;

    public ClientConnection(Path p, long fs, FileOutputStream f) {
        pathToFile = p;
        fileSize = fs;
        file = f;
    }


    public FileOutputStream getFile() {
        return file;
    }

    public void updateReceived(long rec) {
        received += rec;
        recorded += rec;
    }

    public long resetReceived() {
        long res = received;
        received = 0;
        return res;
    }

    public long getRecorded() {
        return recorded;
    }

    public Path getPathToFile() {
        return pathToFile;
    }

    // файл полностью записан
    public boolean fileFull() {
        return recorded == fileSize;
    }
}
