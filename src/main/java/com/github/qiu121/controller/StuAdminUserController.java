package com.github.qiu121.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.qiu121.common.R;
import com.github.qiu121.common.enumeration.PermissionEnum;
import com.github.qiu121.common.exception.BusinessException;
import com.github.qiu121.common.exception.DuplicateException;
import com.github.qiu121.common.exception.NotFoundException;
import com.github.qiu121.entity.Permission;
import com.github.qiu121.entity.StuAdmin;
import com.github.qiu121.service.PermissionService;
import com.github.qiu121.service.StuAdminService;
import com.github.qiu121.util.SecureUtil;
import com.github.qiu121.vo.StuAdminVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:qiu0089@foxmail.com">qiu121</a>
 * @version 1.0
 * @date 2023/03/17
 * @description 几乎和StudentUserController一致，懒得写😂
 */
@RestController
@Slf4j
@CrossOrigin
@RequestMapping("/users/stuAdmin")
public class StuAdminUserController {

    @Resource
    private StuAdminService stuAdminService;
    @Resource
    private PermissionService permissionService;

    /**
     * 新增组长账户
     *
     * @param stuAdmin
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole("admin")
    public R<Boolean> addUser(@RequestBody StuAdmin stuAdmin) {
        final String username = stuAdmin.getUsername();
        final String password = stuAdmin.getPassword();
        if (StringUtils.isNotBlank(password)) {//哈希加密
            stuAdmin.setPassword(SecureUtil.encrypt(password));
        }
        //查询现有用户名，校验重复数据
        final ArrayList<String> usernameList = new ArrayList<>();
        //查询权限表，而不是用户表
        for (Permission permission : permissionService.list()) {
            usernameList.add(permission.getUsername());
        }
        if (!usernameList.contains(username)) {
            final boolean saveUser = stuAdminService.save(stuAdmin);
            final boolean savePermission = permissionService.save(
                    new Permission(username, PermissionEnum.STU_ADMIN_PERMISSION.getType()));
            if (savePermission & saveUser) {
                log.info("添加完成： {}", true);
                return new R<>(20010, "添加完成");
            }
        } else {
            throw new DuplicateException("添加失败，该账户已存在");
        }
        return new R<>(20012, "添加失败");

    }

    /**
     * 批量删除组长账户
     *
     * @param idArray id数组
     * @return
     */
    @DeleteMapping("/removeBatch/{idArray}")
    @SaCheckRole("admin")
    public R<Boolean> removeBatchUser(@PathVariable Long[] idArray) {

        //根据 用户id 查询用户名
        final QueryWrapper<StuAdmin> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(StuAdmin::getUsername)
                .in(StuAdmin::getId, Arrays.asList(idArray));
        final ArrayList<String> usernameList = new ArrayList<>();
        for (StuAdmin stuAdmin : stuAdminService.list(wrapper)) {
            usernameList.add(stuAdmin.getUsername());
        }

        //根据用户名,删除权限表中对应数据
        final LambdaQueryWrapper<Permission> queryWrapper = new QueryWrapper<Permission>().lambda();
        queryWrapper.in(Permission::getUsername, usernameList)
                .eq(Permission::getType, PermissionEnum.STU_ADMIN_PERMISSION.getType());
        final boolean removedPermission = permissionService.remove(queryWrapper);
        final boolean removedUser = stuAdminService.removeByIds(Arrays.asList(idArray));

        log.info("删除完成： {}", removedPermission & removedUser);
        return removedPermission & removedUser ?
                new R<>(20021, "删除完成") :
                new R<>(20022, "删除失败");
    }

    /**
     * 根据 id 查询组长账户信息
     *
     * @param id
     * @return
     */
    @GetMapping("/get/{id}")
    @SaCheckRole("admin")
    public R<StuAdminVo> getUser(@PathVariable Long id) {
        final StuAdmin stuAdmin = stuAdminService.getById(id);
        Integer code = 20040;
        String msg = "查询完成";
        if (stuAdmin != null) {
            log.info("查询完成");
            return new R<>(code, msg, new StuAdminVo(stuAdmin));
        }
        return new R<>(code, msg, null);

    }

    /**
     * 修改信息员组长账户密码
     *
     * @param stuAdmin 组长账户
     * @return R
     */
    @PutMapping("/update/secure")
    @SaCheckRole("stuAdmin")
    public R<?> updateUserPassword(@RequestParam String old, @RequestBody StuAdmin stuAdmin) {
        // 验证旧密码
        StuAdmin stuAdminOne = stuAdminService.getOne(new LambdaQueryWrapper<StuAdmin>()
                .eq(StuAdmin::getUsername, stuAdmin.getUsername()));
        if (stuAdminOne == null) {
            throw new NotFoundException("账户不存在");
        } else {
            if (!SecureUtil.verify(old, stuAdminOne.getPassword())) {
                return new R<>(20033, "旧密码错误");
            }
            if (Objects.equals(stuAdmin.getPassword(), old)) {
                throw new BusinessException("新密码与原密码相同，请重试");
            }
        }

        // 修改新密码
        stuAdmin.setPassword(SecureUtil.encrypt(stuAdmin.getPassword()));

        final LambdaUpdateWrapper<StuAdmin> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StuAdmin::getUsername, stuAdmin.getUsername())
                .set(StringUtils.isNotBlank(stuAdmin.getPassword()), StuAdmin::getPassword, stuAdmin.getPassword());
        final boolean success = stuAdminService.update(wrapper);
        return success ? new R<>(20031, "修改完成") :
                new R<>(20032, "修改失败");
    }

    /**
     * 修改组长用户信息
     *
     * @param stuAdmin
     * @return
     */
    @PutMapping("/update")
    @SaCheckRole("admin")
    private R<Boolean> updateUser(@RequestBody StuAdmin stuAdmin) {

        final String oldPassword = stuAdmin.getPassword();
        if (StringUtils.isNotBlank(oldPassword)) {
            stuAdmin.setPassword(SecureUtil.encrypt(stuAdmin.getPassword()));
        }

        final LambdaUpdateWrapper<StuAdmin> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StuAdmin::getId, stuAdmin.getId())
                .set(StringUtils.isNotBlank(stuAdmin.getName()), StuAdmin::getName, stuAdmin.getName())
                .set(StringUtils.isNotBlank(stuAdmin.getUsername()), StuAdmin::getUsername, stuAdmin.getUsername())
                .set(StringUtils.isNotBlank(stuAdmin.getPassword()), StuAdmin::getPassword, stuAdmin.getPassword())
                .set(StringUtils.isNotBlank(stuAdmin.getClassName()), StuAdmin::getClassName, stuAdmin.getClassName())

                //限定的输入格式，不需要判空
                .set(StuAdmin::getCollege, stuAdmin.getCollege())
                .set(StuAdmin::getEnrollmentYear, stuAdmin.getEnrollmentYear());

        final boolean success = stuAdminService.update(wrapper);

        log.info("修改完成: {}", success);
        return success ? new R<>(20031, "修改完成") :
                new R<>(20032, "修改失败");
    }

    /**
     * 动态条件(学院、班级)、分页询组长账户
     *
     * @param stuAdmin   信息员组长对象
     * @param currentNum 当前页号
     * @param pageSize   每页条数
     * @return R<IPage < StuAdmin>>
     */
    @PostMapping("/list/{currentNum}/{pageSize}")
    @SaCheckRole("admin")
    public R<IPage<StuAdminVo>> list(@RequestBody StuAdmin stuAdmin,
                                     @PathVariable long currentNum,
                                     @PathVariable long pageSize) {
        final LambdaQueryWrapper<StuAdmin> wrapper = new QueryWrapper<StuAdmin>().lambda();
        wrapper.like(StringUtils.isNotBlank(stuAdmin.getCollege()), StuAdmin::getCollege, stuAdmin.getCollege())
                .like(StringUtils.isNotBlank(stuAdmin.getClassName()), StuAdmin::getClassName, stuAdmin.getClassName())
                .like(StringUtils.isNotBlank(stuAdmin.getEducationLevel()), StuAdmin::getEducationLevel, stuAdmin.getEducationLevel());

        final Page<StuAdmin> page = stuAdminService.page(new Page<>(currentNum, pageSize), wrapper);


        // 将 List<StuAdmin> 转化为 List<StudentAdminVo>
        List<StuAdminVo> stuAdminVoLists = page.getRecords().stream()
                .map(s -> {
                    StuAdminVo stuAdminVo = new StuAdminVo();
                    BeanUtils.copyProperties(s, stuAdminVo);
                    return stuAdminVo;
                }).collect(Collectors.toList());

        // 将 List<StudentVo> 封装到 Page<StudentVo> 并返回
        Page<StuAdminVo> stuAdminPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        stuAdminPage.setRecords(stuAdminVoLists);

        log.info("查询完成: {}", stuAdminPage);
        return new R<>(20040, "查询完成", stuAdminPage);

    }

}

