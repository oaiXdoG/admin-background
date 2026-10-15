package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.LogSource;
import lombok.Getter;
import lombok.Setter;

/** 不返回密码，只返回是否已设置 */
@Getter
@Setter
public class LogSourceResponse {

    private String url;
    private String database;
    private String readUser;
    private boolean readPasswordSet;
    private long updatedAt;

    public static LogSourceResponse valueOf(LogSource source) {
        LogSourceResponse response = new LogSourceResponse();
        response.url = source.getUrl();
        response.database = source.getDatabase();
        response.readUser = source.getReadUser();
        response.readPasswordSet = source.getReadPassword() != null && !source.getReadPassword().isBlank();
        response.updatedAt = source.getUpdatedAt();
        return response;
    }
}
