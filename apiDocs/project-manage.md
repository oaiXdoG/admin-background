# 项目管理列表

`POST /api/project/manage/list`，请求体 `{}`。需要平台菜单 `manage_project`。

返回全部项目（包括已关闭），按 ID 排序。每项字段：`projectId`、`appKey`、`name`、`description`、`status`（1 正常，2 已关闭）、`memberCount`、`createdAt`、`updatedAt`。

错误：`100003` 未登录，`100016` 无权访问。
