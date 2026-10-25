package com.example.cineone_admin.sync;

public class SyncAlreadyRunningException extends RuntimeException {
    public SyncAlreadyRunningException() { super("Nhà cung cấp đang có một lượt đồng bộ chưa kết thúc."); }
}
