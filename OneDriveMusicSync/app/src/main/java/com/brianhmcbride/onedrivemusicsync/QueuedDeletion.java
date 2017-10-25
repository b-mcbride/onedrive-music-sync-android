package com.brianhmcbride.onedrivemusicsync;

class QueuedDeletion {
    private String fileSystemPath;

    QueuedDeletion(String fileSystemPath){
        setFileSystemPath(fileSystemPath);
    }

    String getFileSystemPath() {
        return this.fileSystemPath;
    }

    private void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }
}
