# 项目成员

需要当前项目中的项目菜单 `project_member`；超级管理员在任何项目中都可以使用。所有请求携带 `projectId`，必须等于会话当前项目。

| 接口 | 请求体 | 说明 |
| --- | --- | --- |
| `/api/project/member/list` | `{"projectId"}` | 成员列表：`accountId`、`account`、`username`、`status`、`roleId`、`roleName`、`createdAt`（加入时间） |
| `/api/project/member/add` | `{"projectId","account","roleId"}` | 按登录名添加已有账号 |
| `/api/project/member/update` | `{"projectId","accountId","roleId"}` | 修改成员的项目角色 |
| `/api/project/member/remove` | `{"projectId","accountId"}` | 移除成员，不能移除自己 |

`roleId` 必须是本项目已启用的项目角色。超级管理员不需要成为成员即可进入项目。

错误：`100001` 参数不合法，`100003` 未登录，`100005` 不能访问该项目，`100016` 无权访问，`100027` 项目不一致，`100028` 账号不存在，`100029` 角色不属于本项目或已停用，`100033` 已经是项目成员，`100034` 项目成员不存在，`100035` 不能对自己执行该操作。
