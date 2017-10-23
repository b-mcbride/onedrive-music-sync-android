package com.brianhmcbride.onedrivemusicsync;

/**
 * Created by brian on 10/22/2017.
 */

public class QueuedDeletion {
    private String fileSystemPath;

    public QueuedDeletion(String fileSystemPath){
        setFileSystemPath(fileSystemPath);
    }

    public String getFileSystemPath() {
        return this.fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }
}
