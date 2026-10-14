# 系统设置改造方案（平台 / 模板 / 项目三层角色）

状态：已实施（2026-10-13），正式库已迁移。迁移前备份库：`admin_background_bak_premigrate`。

## 一、已确定的决策

| # | 决策 |
| --- | --- |
| 1 | 角色分三类：平台角色、角色模板、项目角色。模板由平台维护，**复制**到项目后成为项目角色，各项目独立维护权限；修改模板不影响已复制的角色 |
| 2 | 超级管理员可以切换所有未关闭项目，并且在任何项目里拥有该项目全部项目菜单权限，不需要成为项目成员 |
| 3 | 账号只由平台创建；项目管理员在「成员管理」里按登录名把已有账号拉进项目 |
| 4 | 项目用 `status`（1 正常 / 2 已关闭）替换 `closed`，与其他对象统一 |
| 5 | 按钮级权限（菜单类型 3）放到下一阶段（已于 2026-10-14 实施，见 `permission-flow.md`） |

同时纳入本次改造（此前方案中已提出）：

- 当前项目跟随登录会话保存，不再以账号为准，多标签页 / 多设备互不覆盖
- 密码改为 bcrypt 哈希存储
- 账号增加启用 / 禁用；禁用、改密码时让该账号已登录的会话失效
- 新增退出登录接口

## 二、通用约定

- 状态统一：`1` 启用（项目为正常），`2` 停用（项目为已关闭）
- 时间统一：毫秒时间戳 `bigint`；所有对象增加 `created_at`、`updated_at`
- 菜单与角色的「范围」：`1` 平台，`2` 项目
- 权限判断：平台接口只认平台角色；项目接口只认当前会话项目里的项目角色，超级管理员直接通过

## 三、四个对象

### 1. 菜单 `menu`

| 字段 | 类型 | 变化 | 规则 |
| --- | --- | --- | --- |
| id | bigint | — | 手动指定 |
| parent_id | bigint | — | 为空表示顶级；目录和菜单的上级必须是目录，按钮的上级必须是菜单 |
| menu_type | smallint | — | 1 目录，2 菜单，3 按钮 |
| scope | smallint | **新增** | 1 平台，2 项目。只在顶级目录选择，子项创建和移动时自动取上级的值；有子项的顶级目录不能改范围 |
| name | varchar(64) | — | 必填 |
| icon | varchar(64) | — | iconify 名称 |
| route_name | varchar(64) | — | 菜单和按钮必填，全局唯一；按钮保存权限标识，例如 `manage_user:create` |
| route_path | varchar(128) | — | 选择已注册路由时带出 |
| status | smallint | — | 1 启用，2 停用 |
| hidden | boolean | — | 只影响侧栏显示 |
| built_in | boolean | — | 内置菜单不能修改、删除 |
| sort_order | int | — | 同级排序 |
| created_at / updated_at | bigint | **新增** | |

预置菜单调整：

```
系统管理  scope=1   项目管理 manage_project / 用户管理 manage_user / 角色管理 manage_role
                    角色模板 manage_role_template（新增）/ 菜单管理 manage_menu
项目设置  scope=2   成员管理 project_member（新增）/ 角色管理 project_role（新增）
数据中心  scope=2   数据查询 / 日志查询 / 查询模板管理
服务器管理 scope=2  性能监控 / 运维操作
```

界面（系统管理 → 菜单管理）：

- 搜索：名称/路由、范围、状态
- 表格（树）：名称、ID、类型、**范围**、路由名称、路由路径、状态、隐藏、排序、操作
- 表单：菜单 ID、上级目录、类型、**范围**（仅顶级目录可选，子项只读显示）、已注册路由、名称、图标、状态、隐藏、排序

### 2. 角色 `role`

| 字段 | 类型 | 变化 | 规则 |
| --- | --- | --- | --- |
| id | bigint | — | |
| role_type | smallint | **新增，替换 global_role** | 1 平台角色，2 角色模板，3 项目角色 |
| project_id | bigint | **新增** | 项目角色必填，其余为空 |
| template_id | bigint | **新增** | 项目角色的来源模板，只做展示，可为空 |
| name | varchar(32) | 唯一范围改变 | 同一类型、同一项目内唯一 |
| code | varchar(64) | 唯一范围改变 + 格式 | 同上唯一；只允许大写字母、数字、下划线，字母开头 |
| description | varchar(128) | — | |
| status | smallint | — | |
| built_in | boolean | — | 超级管理员为内置平台角色 |
| created_at / updated_at | bigint | **新增** | |

唯一约束：`unique (role_type, coalesce(project_id, 0), name)`，`code` 同理。

可分配菜单：平台角色只能分配平台菜单；模板和项目角色只能分配项目菜单。子菜单必须带上级目录（已上线）。

三类角色的操作位置：

| | 平台角色 | 角色模板 | 项目角色 |
| --- | --- | --- | --- |
| 管理位置 | 系统管理 → 角色管理 | 系统管理 → 角色模板 | 项目设置 → 角色管理（当前项目） |
| 需要的菜单 | manage_role | manage_role_template | 当前项目的 project_role |
| 分配给账号 | `account_role` | 不直接分配 | `project_user` |
| 生效时机 | 一直 | — | 切到该项目时 |

新建项目时自动生成内置项目角色「项目管理员」（`PROJECT_ADMIN`，拥有「项目设置」全部菜单），并按勾选复制模板。项目里也可以随时「从模板导入」，同名角色已存在时跳过。

界面：

- 角色管理（平台）/ 角色模板：搜索名称、状态；表格 ID、名称、编码、描述、状态（行内开关）、创建时间、操作（编辑、分配菜单、删除）
- 角色管理（项目）：同上，多一列「来源模板」，工具栏多「从模板导入」
- 分配菜单弹窗：只显示该角色范围内的菜单树

### 3. 账号 `account`

| 字段 | 类型 | 变化 | 规则 |
| --- | --- | --- | --- |
| id | bigint | — | |
| account | varchar(64) | — | 登录名，不能修改 |
| password_hash | varchar(100) | **替换 password** | bcrypt；任何接口都不返回 |
| username | varchar(64) | — | 显示名 |
| status | smallint | **新增** | 1 启用，2 禁用；禁用账号不能登录，已登录会话立即失效；不能禁用自己 |
| remark | varchar(128) | **新增** | 备注 |
| last_login_at | bigint | **新增** | |
| last_project_id | bigint | 语义调整 | 只作为下次登录默认进入的项目；当前项目改由会话保存 |
| created_at / updated_at | bigint | **新增** | |
| admin | boolean | **删除** | 迁移完成后不再使用 |

关联：

- `account_role(account_id, role_id)`：只能是平台角色。只有超级管理员能分配「超级管理员」
- `project_user(project_id, account_id, role_id, created_at)`：role_id 必须是该项目自己的项目角色；新增 `created_at` 作为加入时间

界面：

- 系统管理 → 用户管理：搜索登录名/显示名、状态、所属项目；表格 ID、登录名、显示名、**平台角色**、**项目数**、状态（行内开关）、最后登录、创建时间、操作（编辑、分配平台角色、关联项目）
  - 编辑：显示名、备注、重置密码
  - 关联项目：每行选项目后，角色下拉只列出该项目的项目角色
- 项目设置 → 成员管理（当前项目）：表格 登录名、显示名、项目角色、加入时间、操作（修改角色、移除）；工具栏「添加成员」按登录名添加已有账号

### 4. 项目 `project`

| 字段 | 类型 | 变化 | 规则 |
| --- | --- | --- | --- |
| id | bigint | — | |
| app_key | varchar(64) | — | 唯一，可随机生成；创建后不可修改（日志按它隔离），`/api/project/update` 带了不同的值返回 `100040` |
| name | varchar(64) | — | 必填 |
| description | varchar(128) | **新增** | 可空 |
| status | smallint | **替换 closed** | 1 正常，2 已关闭；关闭后成员关系保留，重新启用即恢复 |
| created_at | bigint | 旧数据补值 | |
| updated_at | bigint | **新增** | |

界面（系统管理 → 项目管理，数据改走专用管理接口）：

- 搜索：名称、App Key、状态（可查看已关闭）
- 表格：ID、名称、App Key、描述、**成员数**、状态、创建时间、操作（编辑、关闭 / 重新启用、查看成员）
- 新建项目：App Key、名称、描述、**初始角色模板**（多选）
- 右上角项目切换器不变：超级管理员看到全部正常项目，其他账号看到自己加入的正常项目

## 四、接口清单

沿用 `POST` + JSON + `{code,message,data}`。「平台」表示需要对应的平台菜单，「项目」表示需要当前会话项目中的对应项目菜单。

| 接口 | 权限 | 说明 |
| --- | --- | --- |
| `/api/login` | — | 返回字段不变；校验账号状态，记录 last_login_at，会话项目初始化为 last_project_id |
| `/api/logout` | 登录 | **新增**，删除当前会话 |
| `/api/project/list`、`/api/project/switch` | 登录 | 不变；switch 写入会话项目 |
| `/api/access/menu` | 登录 | 平台菜单来自平台角色；项目菜单来自当前项目角色，超级管理员为全部项目菜单 |
| `/api/menu/*` | 平台 manage_menu | 读写增加 `scope` |
| `/api/role/*` | 平台 manage_role | 只处理平台角色 |
| `/api/role-template/list`、`create`、`update`、`remove`、`menu/list`、`menu/update` | 平台 manage_role_template | **新增** |
| `/api/project/manage/list` | 平台 manage_project | **新增**，含已关闭项目和成员数 |
| `/api/project/create` | 平台 manage_project | 增加 `description`、`templateIds` |
| `/api/project/update` | 平台 manage_project | 增加 `description` |
| `/api/project/close`、`/api/project/reopen` | 平台 manage_project | reopen **新增** |
| `/api/account/list` | 平台 manage_user | 增加 status、remark、platformRoles、projectCount、lastLoginAt、createdAt |
| `/api/account/create`、`update` | 平台 manage_user | 增加 remark；密码按哈希保存 |
| `/api/account/status` | 平台 manage_user | **新增**，启用 / 禁用 |
| `/api/account/role/update` | 平台 manage_user | **新增**，分配平台角色 |
| `/api/account/project/list`、`update` | 平台 manage_user | 角色必须属于对应项目 |
| `/api/account/project/roles` | 平台 manage_user | **新增**，按项目列出可选项目角色 |
| `/api/project/member/list`、`add`、`update`、`remove` | 项目 project_member | **新增** |
| `/api/project/role/list`、`create`、`update`、`remove`、`menu/list`、`menu/update`、`import` | 项目 project_role | **新增**，只操作当前项目的角色 |

## 五、会话与密码

- Redis：`login:token:<token>` → 账号 ID（不变）；**新增** `login:project:<token>` → 当前项目 ID；**新增** `login:account:<id>` → 该账号的令牌集合，用于禁用、改密码、退出时批量失效。TTL 与令牌一致（12 小时）
- 项目接口以会话项目为准；客户端携带的 projectId 与会话不一致时仍返回 `100027`
- 密码使用 `spring-security-crypto` 的 `BCryptPasswordEncoder`（新增依赖）
- （2026-10-14）会话改为滑动过期：每次通过 `requireAccount` 的请求把令牌、会话项目和令牌集合续到 12 小时，12 小时无操作才失效
- （2026-10-14）密码规则 `PasswordPolicy`：8～128 位，同时包含字母和数字；新建账号、重置密码、本人改密码都校验，不符合返回 `100050`。已有账号的旧密码不受影响，下次修改时生效
- （2026-10-14）登录防爆破：`login:fail:account:<登录名小写>`、`login:fail:ip:<IP>` 记录失败次数，从第一次失败起 15 分钟；账号 5 次或 IP 20 次后返回 `100049`，锁定期间密码正确也不登录；登录成功清除该账号的计数。IP 取连接地址，只有连接来自本机（同机 nginx）时才信任 `X-Real-IP`

## 六、存量数据迁移

已于 2026-10-13 完成。之后数据库按 `schema.sql` 重新初始化，一次性迁移脚本和启动时的明文密码转换代码已删除。初始化过程见 `docs/initialization.md`。

## 七、实施顺序

1. 后端：表结构、实体、迁移脚本
2. 后端：权限核心（会话项目、访问菜单、超级管理员规则、密码哈希、退出、禁用失效）
3. 后端：模板、项目角色、成员、项目管理、账号相关接口及文档
4. 前端：系统管理四个页面调整 + 角色模板 + 项目设置两个页面 + 新建项目选择模板
5. 联调：真实后端全流程测试后提交
