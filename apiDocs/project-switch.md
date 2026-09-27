# 切换项目

`POST /api/project/switch`

请求体：`{"projectId":1}`。

处理顺序：校验令牌和项目编号；超级管理员校验项目存在即可切换；其他账号必须存在对应的 `project_user`；通过后更新 `account.last_project_id`。

错误：`100001` 参数不合法，`100003` 未登录，`100005` 不能切换到该项目。
