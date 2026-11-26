package com.tenantwall.demo.model;

public class ModifyResponse {
    public boolean success;
    public String result;
    public String error;
    public String engine;      // "local" أو "python"
    public long timeMs;
    public boolean cached;

    public static ModifyResponse ok(String result, String engine, long timeMs) {
        ModifyResponse r = new ModifyResponse();
        r.success = true;
        r.result = result;
        r.engine = engine;
        r.timeMs = timeMs;
        return r;
    }

    public static ModifyResponse fail(String error) {
        ModifyResponse r = new ModifyResponse();
        r.success = false;
        r.error = error;
        return r;
    }
}