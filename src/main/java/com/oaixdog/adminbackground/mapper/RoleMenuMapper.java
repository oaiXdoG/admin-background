package com.oaixdog.adminbackground.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oaixdog.adminbackground.entity.RoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {

    @Select("<script>select menu_id from role_menu where role_id in <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach></script>")
    List<Long> listMenuIds(@Param("roleIds") List<Long> roleIds);
}
