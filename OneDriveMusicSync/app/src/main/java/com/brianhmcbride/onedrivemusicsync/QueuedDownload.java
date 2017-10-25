package com.brianhmcbride.onedrivemusicsync;

import android.net.Uri;

class QueuedDownload {
    private long downloadId;
    private Uri downloadUri;
    private String fileSystemPath;
    private boolean isDownloadComplete;

    QueuedDownload(Uri downloadUri, String fileSystemPath) {
        setDownloadUri(downloadUri);
        setFileSystemPath(fileSystemPath);
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
}