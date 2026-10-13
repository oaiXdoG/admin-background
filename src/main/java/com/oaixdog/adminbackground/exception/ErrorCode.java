package com.oaixdog.adminbackground.exception;

/**
 * 业务错误码。
 * 只表示接口对这次请求做出的业务判断。
 * 数据库连不上、空指针、未知异常不在这里，接口也不把它们包装成错误码返回。
 */
public enum ErrorCode {

    /**
     * 判断的是入参本身：字段为空，或超出接口约定的长度。登录里是 account、password 为空或超长，以及请求体无法按登录请求读取。
     * 不表示账号不存在，也不表示服务器内部故障。
     */
    INVALID_REQUEST(100001, "请求参数不合法"),

    /**
     * 判断的是登录凭证：账号不存在，或密码与账号表 password 不一致。两种情况同一个码，不告诉调用方是哪一种。
     * 不表示查询过程中的数据库故障。
     */
    LOGIN_FAILED(100002, "账号或密码错误"),

    /**
     * 判断的是后续请求的令牌：没有携带，或已经失效。
     * 登录接口不抛这个码。不表示服务器拒绝连接。
     */
    UNAUTHENTICATED(100003, "未登录"),

    /**
     * 判断的是当前账号没有项目管理菜单权限，不能创建项目。
     * 不表示令牌失效，也不表示项目名不合法。
     */
    PROJECT_CREATE_FORBIDDEN(100004, "无权创建项目"),

    /**
     * 判断的是目标项目没有挂在当前账号上，不能把它记为当前项目。
     * 项目不存在和未挂载是同一个码，不区分。不表示令牌失效。
     */
    PROJECT_SWITCH_FORBIDDEN(100005, "不能切换到该项目"),

    /**
     * 判断的是登录名已经存在，不能再创建一条相同的 account。
     * 不表示密码不合法，也不表示当前操作者不是管理员。
     */
    ACCOUNT_EXISTS(100006, "登录名已存在"),

    /**
     * 判断的是当前账号没有用户管理菜单权限，不能创建账号。
     * 不表示令牌失效，也不表示登录名或密码不合法。
     */
    ACCOUNT_CREATE_FORBIDDEN(100007, "无权创建账号"),

    /**
     * 判断的是当前账号没有菜单管理权限，不能维护菜单。
     * 不表示令牌失效，也不表示菜单字段不合法。
     */
    MENU_MANAGE_FORBIDDEN(100008, "无权管理菜单"),

    /**
     * 判断的是要操作的菜单不存在。
     * 不表示父级菜单不合法，也不表示菜单下还有内容。
     */
    MENU_NOT_FOUND(100009, "菜单不存在"),

    /**
     * 判断的是父级不能用：不存在、选了自己、选了自己的下级，或父级是菜单。
     * 子项只能挂在目录下面。
     */
    MENU_PARENT_INVALID(100010, "父级菜单不合法"),

    /**
     * 判断的是下面还有子项，不能删除。
     * 不表示菜单不存在。
     */
    MENU_NOT_EMPTY(100011, "下面还有子项，不能删除"),

    /**
     * 判断的是目录下面还有子项，不能改成菜单。
     * 不表示删除。
     */
    MENU_TYPE_CHANGE_FORBIDDEN(100015, "目录下还有子项，不能改成菜单"),

    PERMISSION_DENIED(100016, "无权访问"),

    BUILT_IN_MENU_FORBIDDEN(100017, "内置菜单不能修改或删除"),

    ROLE_NOT_FOUND(100018, "角色不存在"),
    ROLE_NAME_EXISTS(100019, "角色名称已存在"),
    BUILT_IN_ROLE_FORBIDDEN(100020, "内置角色不能修改或删除"),
    ROLE_IN_USE(100021, "角色正在使用，不能删除"),
    ROLE_MENU_INVALID(100022, "角色菜单分配不合法"),
    ROLE_CODE_EXISTS(100023, "角色编码已存在"),
    PROJECT_APP_KEY_EXISTS(100024, "App Key 已存在"),
    PROJECT_NOT_FOUND(100025, "项目不存在或已关闭"),
    MENU_ID_EXISTS(100026, "菜单 ID 已存在"),
    PROJECT_CONTEXT_MISMATCH(100027, "请求项目与当前项目不一致，请切换到正确项目后重试"),
    ACCOUNT_NOT_FOUND(100028, "账号不存在"),
    ACCOUNT_PROJECT_INVALID(100029, "项目关联不合法"),
    MENU_ROUTE_NAME_EXISTS(100030, "路由名称已被其他菜单使用"),
    ACCOUNT_DISABLED(100031, "账号已禁用"),
    MENU_SCOPE_CHANGE_FORBIDDEN(100032, "有子项的顶级目录不能修改范围"),
    MEMBER_EXISTS(100033, "该账号已经是项目成员"),
    MEMBER_NOT_FOUND(100034, "项目成员不存在"),
    SELF_OPERATION_FORBIDDEN(100035, "不能对自己执行该操作"),
    SUPER_ADMIN_REQUIRED(100036, "只有超级管理员可以分配超级管理员角色"),
    MENU_BLOCK_FULL(100037, "该顶级目录下的菜单数量已达上限");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
