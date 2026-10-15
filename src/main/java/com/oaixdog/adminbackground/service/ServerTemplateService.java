package com.oaixdog.adminbackground.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oaixdog.adminbackground.entity.ServerJvmTemplate;
import com.oaixdog.adminbackground.entity.ServerProcess;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.oaixdog.adminbackground.mapper.ServerJvmTemplateMapper;
import com.oaixdog.adminbackground.mapper.ServerProcessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JVM 参数模板：同一项目多个进程共用的 JVM 参数，进程启动时模板参数在前、进程自己的参数在后 */
@Service
@RequiredArgsConstructor
public class ServerTemplateService {

    private static final String MENU = "server_process";
    private static final int NAME_MAX_LENGTH = 64;

    private final PermissionService permissionService;
    private final ServerJvmTemplateMapper templateMapper;
    private final ServerProcessMapper processMapper;

    public List<Map<String, Object>> list(String authorization, Long projectId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        return templateMapper.selectList(Wrappers.<ServerJvmTemplate>lambdaQuery().eq(ServerJvmTemplate::getProjectId, access.projectId())
                .orderByAsc(ServerJvmTemplate::getId)).stream().map(this::toResponse).toList();
    }

    public Map<String, Object> save(String authorization, Long projectId, Long templateId, String name, String options) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        String templateName = name == null ? "" : name.trim();
        String templateOptions = ServerProcessService.normalizeOptions(options);
        if (templateName.isEmpty() || templateName.length() > NAME_MAX_LENGTH || templateOptions == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long now = System.currentTimeMillis();
        ServerJvmTemplate template = templateId == null ? new ServerJvmTemplate() : require(access.projectId(), templateId);
        if (templateId == null) {
            template.setProjectId(access.projectId());
            template.setCreatedAt(now);
        }
        boolean optionsChanged = templateId != null && !templateOptions.equals(template.getOptions());
        template.setName(templateName);
        template.setOptions(templateOptions);
        template.setUpdatedAt(now);
        if (templateMapper.selectCount(Wrappers.<ServerJvmTemplate>lambdaQuery().eq(ServerJvmTemplate::getProjectId, access.projectId())
                .eq(ServerJvmTemplate::getName, templateName).ne(templateId != null, ServerJvmTemplate::getId, templateId)) > 0) {
            throw new BusinessException(ErrorCode.JVM_TEMPLATE_NAME_EXISTS);
        }
        try {
            if (templateId == null) {
                templateMapper.insert(template);
            } else {
                templateMapper.updateById(template);
                if (optionsChanged) {
                    bumpProcesses(template.getId(), now);
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.JVM_TEMPLATE_NAME_EXISTS);
        }
        return toResponse(template);
    }

    public void remove(String authorization, Long projectId, Long templateId) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU + ":update");
        ServerJvmTemplate template = require(access.projectId(), templateId);
        if (processMapper.selectCount(Wrappers.<ServerProcess>lambdaQuery().eq(ServerProcess::getTemplateId, template.getId())) > 0) {
            throw new BusinessException(ErrorCode.JVM_TEMPLATE_IN_USE);
        }
        templateMapper.deleteById(template.getId());
    }

    public ServerJvmTemplate require(long projectId, Long templateId) {
        ServerJvmTemplate template = templateId == null ? null : templateMapper.selectById(templateId);
        if (template == null || !Objects.equals(template.getProjectId(), projectId)) {
            throw new BusinessException(ErrorCode.JVM_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    /** 模板参数变了，用它的进程都要重启后才生效 */
    private void bumpProcesses(long templateId, long now) {
        for (ServerProcess process : processMapper.selectList(Wrappers.<ServerProcess>lambdaQuery().eq(ServerProcess::getTemplateId, templateId))) {
            process.setConfigVersion(process.getConfigVersion() + 1);
            process.setUpdatedAt(now);
            processMapper.updateById(process);
        }
    }

    private Map<String, Object> toResponse(ServerJvmTemplate template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("options", template.getOptions());
        result.put("processCount", processMapper.selectCount(Wrappers.<ServerProcess>lambdaQuery().eq(ServerProcess::getTemplateId, template.getId())));
        result.put("updatedAt", template.getUpdatedAt());
        return result;
    }
}
