# 项目切换列表

`POST /api/project/list`，请求体 `{}`。只需登录。

超级管理员返回全部未关闭项目，`roleName` 为「超级管理员」；其他账号返回自己加入的未关闭项目及项目角色。

当前项目依次取：会话当前项目、账号上次使用的项目（`last_project_id`）、列表第一个；结果写回会话和 `last_project_id`。没有可用项目时 `currentProjectId` 省略。

每项字段：`projectId`、`appKey`、`name`、`createdAt`、`roleId`（超级管理员省略）、`roleName`。

错误：`100003` 未登录。

项目管理页面使用 `/api/project/manage/list`，见 `project-manage.md`。
