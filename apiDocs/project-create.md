# 创建项目

`POST /api/project/create`

调用者必须拥有 `manage_project` 菜单权限。处理顺序：校验令牌和权限，校验 App Key 和项目名，校验 App Key 全局唯一，创建项目，写入当前账号的 `last_project_id`。项目不会自动创建或绑定项目角色。

请求体：

```json
{"appKey":"admin-app","name":"管理后台"}
```

`appKey` 手动填写、必填，去除首尾空格后最长 64 字符，区分大小写且全局唯一；`name` 必填，去除首尾空格后最长 64 字符。项目 ID 和创建时间由后端生成，不接受客户端指定。

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "projectId": 1,
    "appKey": "admin-app",
    "name": "管理后台",
    "createdAt": 1790470800000,
    "roleId": null,
    "roleName": null,
    "currentProjectId": 1
  }
}
```

`createdAt` 是创建时生成的 Unix 毫秒时间戳。角色字段为 `null`。超级管理员通过账号级角色访问项目；普通账号需要后续通过项目成员关系绑定角色。

新项目 `closed` 默认为 `false`，可在项目列表中展示。

错误：`100001` 参数不合法，`100003` 未登录，`100016` 无权访问，`100024` App Key 已存在。
