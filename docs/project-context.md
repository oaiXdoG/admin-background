# 项目请求一致性

当前项目以服务器 `account.last_project_id` 为准。角色定义与角色菜单权限沿用现有规则。

## 已接入的流程

- 客户端先获取项目列表，再携带 `currentProjectId` 调用 `/api/access/menu`。
- 切换时先调用 `/api/project/switch`，校验成功响应的项目 ID，再依次获取项目列表和菜单。
- `/api/access/menu` 比较请求 `projectId` 和服务器当前项目，同时检查项目存在、未关闭及账号仍可访问；不一致返回 `100027`。
- 收到不一致错误后，客户端停止项目请求、隐藏旧页面，提示用户通过选择器或确认按钮重新切换。不自动重试业务写入。
- 切换时取消旧项目请求，并按项目版本丢弃迟到响应。菜单更新后重新检查当前路由，清理失效标签页。

## 后续项目业务接口的约束

数据查询、日志、模板、监控、进程和运维接口尚未接入。实现时统一调用 `PermissionService.requireProjectMenu(authorization, projectId, routeName)`，用同一份账号快照检查项目和菜单权限，再检查模板、进程等资源的所属项目。资源归属必须来自服务端保存的配置，不能只信任客户端传来的接口地址或主机。执行过程使用本次校验通过的项目 ID，不重新读取当前项目来决定操作目标。

前端统一通过 `postProject` 发送管理后台的项目请求。此函数携带当前确认的 `projectId`，校验调用者显式提供的项目 ID，并绑定项目切换取消信号。外部模板接口的调用参数仍按其自身协议处理，后台登录令牌不得通过此函数发送到外部地址。

没有当前项目时，只允许菜单初始化使用 `projectId:null`；实际项目业务接口必须提供有效项目 ID。

当前全局定义维护接口与项目管理的目标 ID 沿用原有语义，不把待编辑项目 ID 误作请求的当前项目。

## 官方参考

- [Axios AbortController 取消请求](https://axios-http.com/docs/cancellation)
- [React useSyncExternalStore](https://react.dev/reference/react/useSyncExternalStore)
- [React Effect 依赖](https://react.dev/reference/react/useEffect)
- [Ant Design Modal.destroyAll](https://ant.design/components/modal/)
