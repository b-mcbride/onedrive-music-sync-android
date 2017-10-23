package com.brianhmcbride.onedrivemusicsync;

import android.net.Uri;

/**
 * Created by brian on 10/22/2017.
 */

public class QueuedDownload {
    private long downloadId;
    private Uri downloadUri;
    private String fileSystemPath;
    private boolean isDownloadComplete;

    public QueuedDownload(Uri downloadUri, String fileSystemPath) {
        setDownloadUri(downloadUri);
        setFileSystemPath(fileSystemPath);
    }

    public Uri getDownloadUri() {
        return this.downloadUri;
    }

    public void setDownloadUri(Uri downloadUri) {
        this.downloadUri = downloadUri;
    }

    public String getFileSystemPath() {
        return this.fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public long getDownloadId() {
        return this.downloadId;
    }

    public void setDownloadId(long downloadId) {
        this.downloadId = downloadId;
    }

    public boolean getIsDownloadComplete() {
        return this.isDownloadComplete;
    }

    public void setIsDownloadComplete(boolean isDownloadComplete) {
        this.isDownloadComplete = isDownloadComplete;
    }
}
