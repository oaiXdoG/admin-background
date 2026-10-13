# 权限执行顺序

1. 登录只建立令牌。服务器启动迁移旧账号时，将 `account.admin=true` 的账号绑定到内置“超级管理员”角色，然后把迁移标记改为 `false`。
2. 客户端调用 `/api/project/list` 获取项目上下文，再调用 `/api/access/menu` 获取菜单。
3. `/api/access/menu` 合并账号级角色和当前项目 `project_user.role_id`，查询 `role_menu`，只返回启用菜单。
4. 客户端根据返回菜单渲染侧栏、路由和按钮，不根据 `admin` 字段判断权限。
5. 服务器业务接口再次按目标菜单 `routeName` 查询角色菜单关系；没有权限返回 `100016`。
6. 新建菜单时自动写入超级管理员的 `role_menu`；内置菜单和内置角色由服务器拒绝修改、删除。

当前已接入权限兜底的业务入口：

| 接口 | 权限菜单 |
| --- | --- |
| `/api/account/create` | `manage_user` |
| `/api/account/list`、`/api/account/update` | `manage_user` |
| `/api/account/project/*` | `manage_user` |
| `/api/project/create` | `manage_project` |
| `/api/menu/*` | `manage_menu` |
| `/api/role/*` | `manage_role` |

项目切换仍额外校验目标项目成员关系；超级管理员可以切换全部项目。
