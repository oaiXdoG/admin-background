# 获取项目列表

`POST /api/project/list`，请求体 `{}`。

处理顺序：校验令牌；超级管理员读取全部项目；其他账号读取 `project_user` 中挂载的项目和角色；按 `account.last_project_id` 选择当前项目，不存在时选择列表第一项，并回写账号。

返回 `currentProjectId` 和 `projects`。超级管理员项目项的 `roleName` 为 `超级管理员`；普通账号返回其项目角色。

错误：`100001` 参数不合法，`100003` 未登录。
