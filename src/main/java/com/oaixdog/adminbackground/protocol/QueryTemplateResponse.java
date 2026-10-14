package com.oaixdog.adminbackground.protocol;

import com.oaixdog.adminbackground.entity.QueryTemplate;
import lombok.Getter;
import lombok.Setter;

/** fields 原样返回保存时的 JSON 文本 */
@Getter
@Setter
public class QueryTemplateResponse {

    private long id;
    private String category;
    private String name;
    private String event;
    private String fields;
    private long createdAt;
    private long updatedAt;

    public static QueryTemplateResponse valueOf(QueryTemplate template) {
        QueryTemplateResponse response = new QueryTemplateResponse();
        response.id = template.getId();
        response.category = template.getCategory();
        response.name = template.getName();
        response.event = template.getEvent();
        response.fields = template.getFields();
        response.createdAt = template.getCreatedAt();
        response.updatedAt = template.getUpdatedAt();
        return response;
    }
}
