package com.brianhmcbride.onedrivemusicsync.data;

public class DriveItem {
    private int id;
    private String driveItemId;
    private String fileSystemPath;
    private long downloadId;
    private boolean isDownloadComplete;
    private boolean isMarkedForDeletion;

    public DriveItem(int id, String driveItemId, long downloadId) {
        this.id = id;
        this.driveItemId = driveItemId;
        this.downloadId = downloadId;
    }

    public DriveItem(int id, String driveItemId, String fileSystemPath) {
        this.id = id;
        this.driveItemId = driveItemId;
        this.fileSystemPath = fileSystemPath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDriveItemId() {
        return driveItemId;
    }

    public void setDriveItemId(String driveItemId) {
        this.driveItemId = driveItemId;
    }

    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public long getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(long downloadId) {
        this.downloadId = downloadId;
    }

    public boolean isDownloadComplete() {
        return isDownloadComplete;
    }

    public void setDownloadComplete(boolean downloadComplete) {
        isDownloadComplete = downloadComplete;
    }

    public boolean isMarkedForDeletion() {
        return isMarkedForDeletion;
    }

    public void setMarkedForDeletion(boolean markedForDeletion) {
        isMarkedForDeletion = markedForDeletion;
    }
}

