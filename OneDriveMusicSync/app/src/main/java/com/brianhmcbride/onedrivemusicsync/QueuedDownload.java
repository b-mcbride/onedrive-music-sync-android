package com.brianhmcbride.onedrivemusicsync;

import android.net.Uri;

class QueuedDownload {
    private long downloadId;
    private Uri downloadUri;
    private String fileSystemPath;
    private boolean isDownloadComplete;
    private boolean isMarkedForDownload;

    QueuedDownload(Uri downloadUri, String fileSystemPath) {
        setDownloadUri(downloadUri);
        setFileSystemPath(fileSystemPath);
        setIsMarkedForDownload(false);
    }

    Uri getDownloadUri() {
        return this.downloadUri;
    }

    private void setDownloadUri(Uri downloadUri) {
        this.downloadUri = downloadUri;
    }

    String getFileSystemPath() {
        return this.fileSystemPath;
    }

    private void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    long getDownloadId() {
        return this.downloadId;
    }

    void setDownloadId(long downloadId) {
        this.downloadId = downloadId;
    }

    boolean getIsDownloadComplete() {
        return this.isDownloadComplete;
    }

    void setIsDownloadComplete(boolean isDownloadComplete) {
        this.isDownloadComplete = isDownloadComplete;
    }

    boolean getIsMarkedForDownload() {
        return this.isMarkedForDownload;
    }

    void setIsMarkedForDownload(boolean isMarkedForDownload) {
        this.isMarkedForDownload = isMarkedForDownload;
    }
}