package com.maverickignite.mcp.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_setting")
public class AppSettingEntity {

    @Id
    @Column(name = "key")
    private String key;

    @Column(nullable = false)
    private String value;

    protected AppSettingEntity() {
    }

    public AppSettingEntity(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
