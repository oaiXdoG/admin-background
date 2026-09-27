package com.oaixdog.adminbackground.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AccountProjectMapper {

    @Select("""
            select p.id as project_id, p.app_key, p.name as name, p.created_at, r.id as role_id, r.name as role_name
            from project_user pu
            join project p on p.id = pu.project_id
            join role r on r.id = pu.role_id
            where pu.account_id = #{accountId} and p.closed = false
            order by p.id
            """)
    List<AccountProjectRow> listByAccountId(@Param("accountId") long accountId);

    class AccountProjectRow {

        private Long projectId;
        private String appKey;
        private String name;
        private Long createdAt;
        private Long roleId;
        private String roleName;

        public Long getProjectId() {
            return projectId;
        }

        public void setProjectId(Long projectId) {
            this.projectId = projectId;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public Long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Long createdAt) {
            this.createdAt = createdAt;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getRoleId() {
            return roleId;
        }

        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }
}
