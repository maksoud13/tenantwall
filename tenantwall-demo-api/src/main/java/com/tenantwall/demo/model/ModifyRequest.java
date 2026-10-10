package com.tenantwall.demo.model;

public class ModifyRequest {
    public String sql;
    public String column = "tenant_id";
    public String value;
    public String dialect = "mysql";
    
    public ModifyRequest() {}
}